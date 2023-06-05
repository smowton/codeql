import sys

all_major_versions = len(sys.argv) > 1 and sys.argv[1] == "--all-major-versions"

def version_to_tuple(v):
    bits = v.split("-")
    while len(bits) < 3:
        bits.append("zzz") # No qualifiers --> sorts later than all versions with qualifiers
    version_numbers = bits[0].split(".")
    if len(version_numbers) == 2:
        version_numbers.append("0")
    if len(version_numbers) != 3:
        return (v,)
    try:
        return tuple([int(n) for n in version_numbers] + bits[1:])
    except ValueError:
        return (v,)

def version_cmp(v1, v2):
    vt1 = version_to_tuple(v1)
    vt2 = version_to_tuple(v2)
    if len(vt1) != len(vt2):
        vt1 = (v1,)
        vt2 = (v2,)
    if vt2 > vt1:
        return 1
    elif vt2 < vt1:
        return -1
    else:
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
