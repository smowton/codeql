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
    return result

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

byver = dict()

for l in sys.stdin:
  u_and_i = l.split()
  u = u_and_i[0]
  u_fields = u.split("|")
  if len(u_fields) == 5:
    artifact = (u_fields[0], u_fields[1], u_fields[3]) # groupId, artifactId, classifier
  else:
    artifact = (u_fields[0], u_fields[1])
  version = u_fields[2]
  version_parsed = version_to_tuple(version)
  major_version = None if ((not all_major_versions) or len(version_parsed) == 1) else version_parsed[0]
  key = (artifact, major_version)

  if key not in byver or version_cmp(byver[key][0], version) > 0:
    byver[key] = (version, l)

for (k, v) in byver.items():
  sys.stdout.write(v[1])
