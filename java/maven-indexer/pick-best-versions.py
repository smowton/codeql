import re
import sys

all_major_versions = len(sys.argv) > 1 and sys.argv[1] == "--all-major-versions"

digits = re.compile("^[0-9]+")

# Returns a tuple consisting of the dot-separated integers forming a prefix of 'v', followed by the remainder as a string. For example, 1.2.3.4x5-6 will result in (1, 2, 3, 4, "x5-6")
# Integer segments > 999 are assumed to be text-like rather than version numbers, and also trigger returning the rest of the version as a string.
def version_to_tuple(v):
    result = []
    while len(v) > 0:
        nextnumber = digits.match(v)
        if nextnumber is None:
            break
        nextnumber = nextnumber.group(0)
        if len(nextnumber) > 3:
            break
        result.append(int(nextnumber))
        skip = len(nextnumber)
        if len(v) > skip and v[skip] == ".":
            skip += 1
        v = v[skip:]
    if len(v) != 0:
        result.append(v)
    return adjust_version_tuple(result)

prerelease_re = re.compile("alpha|beta|rc", re.IGNORECASE)

def looks_like_prerelease(s):
    return prerelease_re.search(s) is not None

def adjust_version_tuple(vt):
    if type(vt[-1]) is str and type(vt[0]) is int and looks_like_prerelease(vt[-1]):
        # Demote prereleases one major version, so e.g. we will prefer 1.5.6 to 2.0.0-beta4
        return [vt[0] - 1] + vt[1:]
    else:
        return vt

def element(v, n):
    return v[n] if n < len(v) else None

def type_rank(t):
    # Rank integers before None before text (so e.g. 1.2.3.4 > 1.2.3 > 1.2.3-sometext)
    if t is int:
        return 2
    elif t is type(None):
        return 1
    else:
        return 0

def type_cmp(t1, t2):
    t1val = type_rank(t1)
    t2val = type_rank(t2)
    if t1val > t2val:
        return -1
    elif t1val < t2val:
        return 1
    else:
        return 0

def version_cmp(v1, v2):
    vt1 = version_to_tuple(v1)
    vt2 = version_to_tuple(v2)

    for i in range(max(len(vt1), len(vt2))):
        e1 = element(vt1, i)
        e2 = element(vt2, i)
        if type(e1) != type(e2):
            return type_cmp(type(e1), type(e2))
        elif e1 > e2:
            return -1
        elif e1 < e2:
            return 1

    return 0

# check for characters that would be invalid in file names on Windows. The groupId, artifactId, classifier, and
# version are used by maven in the names of JAR files.
def check_valid_filename(name):
  for c in name:
    if ord(c) < 32 or c in '<>:"/\\|?*':
      return False
  return True

byver = dict()

# HACK: always include at least version 3 of Mockito in addition to the overall latest version.
# This is because that version provides the `Matchers` class, which is widely used and was subsequently removed.
def adjust_major_version(artifact, version, major_version):
  if artifact[0] == "org.mockito" and artifact[1] == "mockito-core" and version[0] == 3:
    return 3
  else:
    return major_version

for l in sys.stdin:
  u_and_i = l.split()
  u = u_and_i[0]
  u_fields = u.split("|")
  if len(u_fields) == 5:
    artifact = (u_fields[0], u_fields[1], u_fields[3]) # groupId, artifactId, classifier
  elif len(u_fields) < 4:
    print("Bad Maven index entry: " + l.strip(), file = sys.stderr)
    continue
  else:
    artifact = (u_fields[0], u_fields[1])
  version = u_fields[2]
  if not (all(map(check_valid_filename, artifact)) and check_valid_filename(version)):
    print("Invalid characters in maven index entry: " + l.strip(), file = sys.stderr)
    continue
  version_parsed = version_to_tuple(version)
  major_version = None if ((not all_major_versions) or len(version_parsed) == 1) else version_parsed[0]
  major_version = adjust_major_version(artifact, version_parsed, major_version)
  key = (artifact, major_version)

  if key not in byver or version_cmp(byver[key][0], version) > 0:
    byver[key] = (version, l)

for (k, v) in byver.items():
  sys.stdout.write(v[1])
