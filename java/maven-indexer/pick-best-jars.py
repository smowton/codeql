# Heuristic: the best jar to provide a given package is the maximum jar scored positively for classes in package and negatively for classes out of package.
# Ordinarily we must list all jars (in score order) that provide at least one unique class (because otherwise the extractor won't know about some perhaps-needed classes)
# Jars with a negative score are omitted entirely even if they provide unique classes on the assumption that they are shading an old version in that so happens to
# provide some since-dropped private class that doesn't in fact matter.
# Classes-in-package ascending should favour newer versions of a package (which typically define less classes) and/or selective shades which have picked a few classes to include
# Classes-out-of-package should exclude jars that have shaded this package in, and/or projects distributed as individual modules as well as an `-all` package.

import re
import sys
import os.path
import listzip
import shutil
import multiprocessing
import concurrent.futures

def read_bytes(fname):
  with open(fname, "rb") as f:
    return f.read()

def _read_jar_index(jarname):
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
  return bypackage

def read_jar_index(jarname):
  try:
    return _read_jar_index(jarname)
  except Exception as e:
    raise Exception("Failed to read " + jarname) from e

# A list of known package prefixes that are too general to use as a heuristic for sharing a common product:
# (very short prefixes like "org" are excluded already):
overly_general_package_prefixes = set([
  ("org", "apache"),
  ("org", "eclipse")
])

def prefix_too_general(prefix):
  if len(prefix) == 1 and len(prefix[0]) <= 4: # Reject very short prefixes like org/, com/, uk/ and so on.
    return True
  return tuple(prefix) in overly_general_package_prefixes

# Return the most general superpackage of package that we estimate indicates another package in the same product.
# Note this might be exactly 'package', indicating its subpackages should be considered neutral, or might be an ancestor, in which case some of 'package's siblings should also be considered netural.
def get_neutral_superpackage(package):
  bits = package.split("/")
  prefixlen = 1
  while prefixlen < len(bits) and prefix_too_general(bits[:prefixlen]):
    prefixlen += 1
  if prefix_too_general(bits[:prefixlen]):
    return None
  else:
    return "/".join(bits[:prefixlen])

def has_neutral_superpackage(package, neutral_superpackage_re):
  return neutral_superpackage_re.match(package) is not None

def get_package_score(package, universal_packages, neutral_superpackage_re):
  if package in universal_packages:
    return 1
  elif has_neutral_superpackage(package, neutral_superpackage_re):
    return 0
  else:
    return -1

def get_jar_score(jarname, jar, universal_packages, neutral_superpackage_re, verbose):
  result = 0
  for (package, classes) in jar.items():
    package_score = get_package_score(package, universal_packages, neutral_superpackage_re)
    nclasses = len(classes)
    total_score = package_score * nclasses
    if verbose:
      print("  %s: score %d * %d classes = %d" % (package, package_score, nclasses, total_score), file = sys.stderr)
    result += total_score
  if verbose:
    print("Total for JAR %s: %d" % (jarname, result), file = sys.stderr)
  return result

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

def pick_best_jars(package, candidates, verbose):
  best_jar = None

  # Packages defined by every candidate -- 99% certainly part of the same product.
  universal_packages = set.intersection(*(set(index.keys()) for (c, index) in candidates))
  # Superpackages of the universal packages -- their children are maybe part of the same product; neither rewarded nor punished.
  neutral_superpackages = map(get_neutral_superpackage, universal_packages)
  neutral_superpackages = set(nsp for nsp in neutral_superpackages if nsp is not None)
  neutral_superpackage_re = re.compile("^(" + "|".join(neutral_superpackages) + ")/")

  candidate_scores = [(c, index, get_jar_score(c, index, universal_packages, neutral_superpackage_re, verbose)) for (c, index) in candidates]

  # Drop candidates with a negative score, unless there is no positive-scoring candidate:
  if any(cis[2] > 0 for cis in candidate_scores):
    candidate_scores = [cis for cis in candidate_scores if cis[2] >= 0]
  else:
    candidate_scores = [max(candidate_scores, key = lambda cis: cis[2])]

  # Sort best first
  candidate_scores = sorted(candidate_scores, key = lambda cis: cis[2], reverse = True)

  # Drop jars that are wholly shadowed by better candidates
  return drop_redundant_candidates(package, candidate_scores)

def _pick_best_jars(args):
  pick_best_jars(*args)

if __name__ == '__main__':

  verbose = any(a == "--verbose" for a in sys.argv)
  max_workers_arg = [a for a in sys.argv if a.startswith("-j")]
  max_workers = int(max_workers_arg[-1][2:]) if len(max_workers_arg) > 0 else None
  explain_arg = [a for a in sys.argv if a.startswith("--explain=")]
  explain_packages = [a[len("--explain="):] for a in explain_arg]
  if len(explain_packages) != 0:
    verbose = True

  try:
    with open(sys.argv[2] + ".input", "r") as f:
      oldjars = dict()
      for l in f:
        packagename = l.split()[0]
        oldjars[packagename] = l.strip()
  except FileNotFoundError as e:
    oldjars = None

  if oldjars is not None:
    try:
      oldresults = dict()
      with open(sys.argv[2], "r") as f:
        for l in f:
          l = l.strip()
          packagename = l.split("=")[0]
          oldresults[packagename] = l
    except FileNotFoundError as e:
      oldjars = None
      oldresults = None

  if len(explain_packages) != 0:
    # To explain a choice, we need to see the original jar contents and evaluate the package choice anew.
    oldjars = None
    oldresults = None

  def should_reuse_result(packagename, inputline):
    return oldjars is not None and oldjars.get(packagename, None) == inputline and oldresults is not None and packagename in oldresults

  needed_jars = set()
  packages_to_compute = []

  with open(sys.argv[1], "r") as f:
    for (i, l) in enumerate(f):
      l = l.strip()
      bits = l.split()
      if len(explain_packages) != 0 and bits[0] not in explain_packages:
        continue
      if bits[1] != "1" and not should_reuse_result(bits[0], l):
        for jar in bits[2:]:
          needed_jars.add(jar)
        packages_to_compute.append((bits[0], bits[2:]))

  needed_jars = list(needed_jars)
  print("Loading indices from %d jars" % len(needed_jars), file = sys.stderr)

  spawn_mp_context = multiprocessing.get_context('spawn')

  with concurrent.futures.ProcessPoolExecutor(mp_context = spawn_mp_context, max_workers = max_workers) as executor:
    loaded_jars = executor.map(read_jar_index, needed_jars)

  jar_indices = dict(zip(needed_jars, loaded_jars))

  print("Computing results for %d packages" % len(packages_to_compute), file = sys.stderr)

  # If we're running to explain a jar choice, don't write ordinary output.
  target_file = sys.argv[2] if len(explain_packages) == 0 else "/dev/null"

  with open(sys.argv[1], "r") as f, open(target_file, "w") as outf:
    for (i, l) in enumerate(f):
      l = l.strip()
      bits = l.split()
      packagename = bits[0]

      if len(explain_packages) != 0 and packagename not in explain_packages:
        continue

      # Referencing an overly general package, such as com/, shouldn't trigger using any jar, even if someone declares a class like `com.MyClass`.
      if prefix_too_general(packagename.split("/")):
        continue

      if bits[1] == "1":
        print("%s=%s" % (packagename, bits[2][:-6]), file = outf)
      elif should_reuse_result(packagename, l):
        print(oldresults[packagename], file = outf)
      else:
        output_jars = pick_best_jars(packagename, [(j, jar_indices[j]) for j in bits[2:]], verbose)
        print("%s=%s" % (packagename, " ".join(j[:-6] for j in output_jars)), file = outf)

  shutil.copy(sys.argv[1], sys.argv[2] + ".input")
