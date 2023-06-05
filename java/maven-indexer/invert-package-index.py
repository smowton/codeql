import os
import os.path
import sys

import listzip

index = dict()

def add_index(fname, index_bytes):
  for l in listzip.listzip(index_bytes):
    if l.endswith(".class"):
      package = os.path.dirname(l)
      if package == "" or package.startswith("META-INF") or package.startswith("BOOT-INF"):
         continue
      if package not in index:
        index[package] = set()
      index[package].add(fname)

def read_bytes(fname):
  with open(fname, "rb") as f:
    return f.read()

i = 0

with open(sys.argv[1], "r") as f:
  for filename in f:
    fileame = filename.strip()
    i += 1
    if i % 1000 == 0:
      print("Indexing ", i, filename, file = sys.stderr)
    try:
      index_file = os.path.join(dirpath, filename)
      cd_file = index_file[:-6] + ".cd"
      add_index(index_file, read_bytes(index_file) + read_bytes(cd_file))
    except Exception as e:
      print("Failed to read index from %s: %s" % (index_file, e), file = sys.stderr)

for (package, files) in sorted(index.items(), key = lambda x: x[0]):
  print(package, len(files), " ".join(sorted(files)))
