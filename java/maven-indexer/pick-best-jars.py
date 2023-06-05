# Heuristic: the best jar to provide a given package is the maximum jar scored positively for classes in package and negatively for classes out of package.
# Ordinarily we must list all jars (in score order) that provide at least one unique class (because otherwise the extractor won't know about some perhaps-needed classes)
# Jars with a negative score are omitted entirely even if they provide unique classes on the assumption that they are shading an old version in that so happens to
# provide some since-dropped private class that doesn't in fact matter.
# Classes-in-package ascending should favour newer versions of a package (which typically define less classes) and/or selective shades which have picked a few classes to include
# Classes-out-of-package should exclude jars that have shaded this package in, and/or projects distributed as individual modules as well as an `-all` package.

import sys
import os.path
import listzip

verbose = len(sys.argv) >= 3 and sys.argv[2] == "--verbose"

jar_index_cache = dict()

def read_bytes(fname):
  with open(fname, "rb") as f:
    return f.read()

def _get_jar_index(jarname):
  if jarname not in jar_index_cache:
    bypackage = dict()
    cd_file = jarname[:-6] + ".cd"
    zip_suffix = read_bytes(jarname) + read_bytes(cd_file)
    for l in listzip.listzip(zip_suffix):
      if l.endswith(".class"):
        lpackage = os.path.dirname(l)
        cname = os.path.basename(l)
        if lpackage not in bypackage:
          bypackage[lpackage] = set()
        bypackage[lpackage].add(cname)
    jar_index_cache[jarname] = bypackage
  return jar_index_cache[jarname]

def get_jar_index(jarname):
  try:
    return _get_jar_index(jarname)
  except Exception as e:
    print("Failed to read " + jarname, file = sys.stderr)
    raise e

def get_neutral_superpackages(package):
  superpackages = []
  package = os.path.dirname(package)
  while package != "":
    superpackages.append(package)
    package = os.path.dirname(package)
  if len(superpackages) > 0 and len(superpackages[-1]) <= 4:
    superpackages.pop() # Don't consider org/, com/, uk/, java/ and so on as meaningful common superpackages (but android/ is a useful common superpackage)
  return superpackages

def has_neutral_superpackage(package, neutral_superpackages):
  package = os.path.dirname(package)
  while package != "":
    if package in neutral_superpackages:
      return True
    package = os.path.dirname(package)
  return False

def get_package_score(package, universal_packages, neutral_superpackages):
  if package in universal_packages:
    return 1
  elif has_neutral_superpackage(package, neutral_superpackages):
    return 0
  else:
    return -1

def get_jar_score(jar, universal_packages, neutral_superpackages):
  return sum(get_package_score(package, universal_packages, neutral_superpackages) * len(classes) for (package, classes) in jar.items())

def drop_redundant_candidates(package, candidate_scores):
  if len(candidate_scores) == 1:
    return [candidate_scores[0][0]]

  result = []
  already_provided = set()
  
  for cis in candidate_scores:
    lenbefore = len(already_provided)
    already_provided.update(cis[1].get(package, []))
    if lenbefore != len(already_provided):
      result.append(cis[0])

  return result

def pick_best_jars(package, candidates):
  best_jar = None

  candidates = [(c, get_jar_index(c)) for c in candidates]
  # Packages defined by every candidate -- 99% certainly part of the same product.
  universal_packages = set.intersection(*(set(index.keys()) for (c, index) in candidates))
  # Superpackages of the universal packages -- their children are maybe part of the same product; neither rewarded nor punished.
  neutral_superpackages = set.union(*(set(get_neutral_superpackages(package)) for package in universal_packages))

  candidate_scores = [(c, index, get_jar_score(index, universal_packages, neutral_superpackages)) for (c, index) in candidates]

  if verbose:
    print("**", package, file = sys.stderr)
    print("\n".join("%s: %d" % (os.path.basename(jar), score) for (jar, index, score) in candidate_scores), file = sys.stderr)

  # Drop candidates with a negative score, unless there is no positive-scoring candidate:
  if any(cis[2] > 0 for cis in candidate_scores):
    candidate_scores = [cis for cis in candidate_scores if cis[2] >= 0]
  else:
    candidate_scores = [max(candidate_scores, key = lambda cis: cis[2])]

  # Sort best first
  candidate_scores = sorted(candidate_scores, key = lambda cis: cis[2], reverse = True)

  # Drop jars that are wholly shadowed by better candidates
  return drop_redundant_candidates(package, candidate_scores)

with open(sys.argv[1], "r") as f:
  for (i, l) in enumerate(f):
    l = l.strip().split()
    if l[1] == "1":
      print("%s=%s" % (l[0], l[2][:-6]))
    else:
      output_jars = pick_best_jars(l[0], l[2:])
      print("%s=%s" % (l[0], ",".join(j[:-6] for j in output_jars)))
