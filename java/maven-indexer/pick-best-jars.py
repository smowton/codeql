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
import utils
import email.utils
import collections

common_prefix_score_bonus = 20

def read_bytes(fname):
  with open(fname, "rb") as f:
    return f.read()

def _read_jar_index(jarname):
  bypackage = dict()
  cd_file = jarname[:-6] + ".cd"
  zip_suffix = read_bytes(jarname) + read_bytes(cd_file)
  for l in listzip.listzip(zip_suffix):
    if l.endswith(".class") and not utils.is_private(l):
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

last_modified_header = "last-modified: "

def read_jar_age(jarname):
  headersname = jarname[:-6] + ".headers"
  with open(headersname, "r") as f:
    for l in f:
      if l.lower().startswith(last_modified_header):
        return email.utils.parsedate_to_datetime(l[len(last_modified_header):])
  return None

def read_jar_data(jarname):
  return (read_jar_index(jarname), read_jar_age(jarname))

# A list of known package prefixes that are too general to use as a heuristic for sharing a common product:
# (very short prefixes like "org" are excluded already):
overly_general_package_prefixes = set([
  ("org", "apache"),
  ("org", "eclipse")
])

def prefix_too_general(prefix):
  if len(prefix) == 0 or (len(prefix) == 1 and len(prefix[0]) <= 4): # Reject very short prefixes like org/, com/, uk/ and so on.
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

def get_common_prefix(l1, l2):
  i = 0
  for (seg1, seg2) in zip(l1, l2):
    if seg1 != seg2:
      break
    i += 1
  return l1[:i]

def adjust_jar_relative_name(relname):
  # HACK: treat commons-io/commons-io like org/apache/commons/commons-io, because a real jar with the latter
  # name exists but only includes older versions of that package.
  # Also treat org/projectlombok as the authoritative provider of the package 'lombok'.
  # Same for org/junit and junit.
  if len(relname) >= 2 and relname[0] == "commons-io" and relname[1] == "commons-io":
    return ["org", "apache", "commons"] + relname[1:]
  if len(relname) >= 2 and relname[0] == "org" and relname[1] == "projectlombok":
    return ["lombok"] + relname[2:]
  if len(relname) >= 1 and relname[0] == "junit":
    return ["org", "junit"] + relname[1:]
  return relname

hamcrest_jar_re = re.compile(".*/hamcrest-[0-9.]*\\.jar.index$")

def adjust_jar_score(jarname, target_package):
  # HACK: prefer hamcrest to hamcrest-all because the former is a renamed version of the latter and adds new matchers while deprecating a couple of classes, making it appear like a worse choice.
  if hamcrest_jar_re.match(jarname) is not None and target_package == "org/hamcrest":
    return 20
  else:
    return 0

def get_jar_score(jarname, jar, target_package, universal_packages, neutral_superpackage_re, jar_repository_dir, verbose):
  result = 0

  # Score jar for the packages it provides -- positively for those clearly related to the target 'package',
  # negatively for those very likely unrelated, and neturally for ambiguous sibling packages.
  for (package, classes) in jar.items():
    package_score = get_package_score(package, universal_packages, neutral_superpackage_re)
    nclasses = len(classes)
    total_score = package_score * nclasses
    if verbose:
      print("  %s: score %d * %d classes = %d" % (package, package_score, nclasses, total_score), file = sys.stderr)
    result += total_score

  # Add a boost to the score if the jar's name substantially matches the package we're looking to provide.
  jar_relative_name = os.path.relpath(jarname, jar_repository_dir)
  if "../" in jar_relative_name:
    raise Exception("JAR name %s should fall within the repository directory %s" % (jarname, jar_repository_dir))
  jar_relative_name_segments = jar_relative_name.split("/")
  jar_relative_name_segments = adjust_jar_relative_name(jar_relative_name_segments)
  target_package_segments = target_package.split("/")
  common_prefix = get_common_prefix(jar_relative_name_segments, target_package_segments)
  if not prefix_too_general(common_prefix):
    if verbose:
      print("BONUS: %d points because the jar name and target package %s have common prefix %s" % (common_prefix_score_bonus, target_package, "/".join(common_prefix)), file = sys.stderr)
    result += common_prefix_score_bonus

  result += adjust_jar_score(jarname, target_package)

  if verbose:
    print("Total for JAR %s: %d" % (jarname, result), file = sys.stderr)
  return result

def drop_redundant_candidates(package, candidate_scores, verbose):
  if len(candidate_scores) == 1:
    return [candidate_scores[0][0]]

  result = []
  already_provided = set()
  
  for cisa in candidate_scores:
    lenbefore = len(already_provided)
    if verbose:
      provided_before = set(already_provided)
    already_provided.update(cisa[1].get(package, []))
    if lenbefore != len(already_provided):
      result.append(cisa[0])
      if verbose and lenbefore != 0:
        print("Jar %s provides additional classes: %s" % (result[-1], "".join("\n" + c for c in sorted(already_provided - provided_before))), file = sys.stderr)

  return result

def get_groupid(jarname):
  # Find ^/^ in the context /root/groupid0/groupid1/.../groupidN^/^artifactid/version/jarname.jar.index
  slashidx = jarname.rindex('/')
  slashidx = jarname.rindex('/', 0, slashidx)
  slashidx = jarname.rindex('/', 0, slashidx)
  return jarname[:slashidx]

one_year_in_seconds = 365 * 24 * 60 * 60

def apply_age_penalty(candidate_scores, verbose):

  # When more than one candidate has the same groupId, apply a multiplicative penalty to artifacts that have been updated less recently.
  # This is intended to distinguish when an artifact has been renamed, so the older version should be deprioritised even if it provides more classes in a given package.
  # Note the adjustment is multiplicative so that positive scores stay positive, and restricted to the same groupId so that someone republishing an old artifact
  # doesn't get prioritised automatically over the 'real' author.

  by_groupid = collections.defaultdict(list)
  for cisa in candidate_scores:
    by_groupid[get_groupid(cisa[0])].append(cisa)

  candidate_penalties = dict()

  for (groupid, cisas) in by_groupid.items():
    if len(cisas) >= 2:
      newest_cisa = max(cisas, key = lambda cisa: cisa[3])
      for cisa in cisas:
        if cisa != newest_cisa:
          age_difference = newest_cisa[3] - cisa[3]
          # Penalty scales linearly from 50% at 5+ years older, to nothing at 1 year older.
          age_penalty = ((age_difference.total_seconds() - one_year_in_seconds) / one_year_in_seconds) * (0.5 / 4)
          age_penalty = min(age_penalty, 0.9)
          age_penalty = max(age_penalty, 0.0)
          if age_penalty > 0:
            if verbose:
              print("Applying age penalty of %.2f to candidate %s" % (age_penalty, cisa[0]), file = sys.stderr)
            candidate_penalties[cisa[0]] = age_penalty

  return [(candidate, index, score * (1 - candidate_penalties.get(candidate, 0)), age) for (candidate, index, score, age) in candidate_scores]

junit_package_re = re.compile("^(org/)?junit(/|$)")

def adjust_universal_packages(packagename, universal_packages):
  # HACK: don't penalise junit for including the package junit/ or org/junit when evaluating the best jar to provide one package or the other.
  # The solution is probably popularity data considering the package is common.
  if junit_package_re.match(packagename) is not None:
    return set.union(universal_packages, set(("junit", "org/junit")))
  else:
    return universal_packages

def pick_best_jars(package, candidates, jar_repository_dir, jar_verbose):
  best_jar = None

  # Packages defined by every candidate -- 99% certainly part of the same product.
  universal_packages = set.intersection(*(set(index.keys()) for (c, (index, age)) in candidates))
  universal_packages = adjust_universal_packages(packagename, universal_packages)

  # Superpackages of the universal packages -- their children are maybe part of the same product; neither rewarded nor punished.
  neutral_superpackages = map(get_neutral_superpackage, universal_packages)
  neutral_superpackages = set(nsp for nsp in neutral_superpackages if nsp is not None)
  neutral_superpackage_re = re.compile("^(" + "|".join(neutral_superpackages) + ")(/|$)")

  candidate_scores = [(c, index, get_jar_score(c, index, package, universal_packages, neutral_superpackage_re, jar_repository_dir, verbose), age) for (c, (index, age)) in candidates]

  # Drop candidates with a negative score, unless there is no positive-scoring candidate:
  if any(cisa[2] > 0 for cisa in candidate_scores):
    candidate_scores = [cisa for cisa in candidate_scores if cisa[2] >= 0]
  else:
    candidate_scores = [max(candidate_scores, key = lambda cisa: cisa[2])]

  candidate_scores = apply_age_penalty(candidate_scores, jar_verbose)

  def best_first_key(cisa):
    # Sort non-tests jars first, and then sort by score.
    return (not cisa[0].endswith("-tests.jar.index"), cisa[2])

  # Sort best first
  candidate_scores = sorted(candidate_scores, key = best_first_key, reverse = True)
  if verbose:
    print("RESULTS before redundnancy elimination:", file = sys.stderr)
    for (candidate, index, score, age) in candidate_scores:
      print("%s: %d" % (candidate, score), file = sys.stderr)

  # Drop jars that are wholly shadowed by better candidates
  results = drop_redundant_candidates(package, candidate_scores, verbose)
  if verbose:
    def get_score(candidate):
      return [cisa for cisa in candidate_scores if cisa[0] == candidate][0][2]

    print("RESULTS after redundnancy elimination:", file = sys.stderr)
    for candidate in results:
      print("%s: %d" % (candidate, get_score(candidate)), file = sys.stderr)

  return results

def _pick_best_jars(args):
  pick_best_jars(*args)

def adjust_output_jars(jars):
  # HACK: manually demote mockito-scala behind other providers.
  # Otherwise this looks more promising than mockito itself -- popularity information will be the correct answer I think.
  if any("mockito" in j for j in jars):
    jars = sorted(jars, key = lambda j: "mockito-scala" in j)
  return jars

if __name__ == '__main__':

  jar_repository_dir = sys.argv[1]
  input_file = sys.argv[2]
  output_file = sys.argv[3]

  verbose = any(a == "--verbose" for a in sys.argv)
  max_workers_arg = [a for a in sys.argv if a.startswith("-j")]
  max_workers = int(max_workers_arg[-1][2:]) if len(max_workers_arg) > 0 else None
  explain_arg = [a for a in sys.argv if a.startswith("--explain=")]
  explain_packages = [a[len("--explain="):] for a in explain_arg]
  if len(explain_packages) != 0:
    verbose = True

  try:
    with open(output_file + ".input", "r") as f:
      oldjars = dict()
      for l in f:
        packagename = l.split()[0]
        oldjars[packagename] = l.strip()
  except FileNotFoundError as e:
    oldjars = None

  if oldjars is not None:
    try:
      oldresults = dict()
      with open(output_file, "r") as f:
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

  with open(input_file, "r") as f:
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
    loaded_jars = executor.map(read_jar_data, needed_jars)

  jar_data = dict(zip(needed_jars, loaded_jars))

  print("Computing results for %d packages" % len(packages_to_compute), file = sys.stderr)

  # If we're running to explain a jar choice, don't write ordinary output.
  target_file = output_file if len(explain_packages) == 0 else "/dev/null"

  with open(input_file, "r") as f, open(target_file, "w") as outf:
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
        output_jars = pick_best_jars(packagename, [(j, jar_data[j]) for j in bits[2:]], jar_repository_dir, verbose)
        output_jars = adjust_output_jars(output_jars)
        print("%s=%s" % (packagename, " ".join(j[:-6] for j in output_jars)), file = outf)

  if len(explain_packages) == 0:
    shutil.copy(input_file, output_file + ".input")
