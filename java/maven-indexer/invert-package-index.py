import os
import os.path
import shutil
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

with open(sys.argv[1], "r") as f:
  jars_to_index = set(l.strip() for l in f)

try:
  with open(sys.argv[2] + ".input", "r") as f:
    existing_index_jars = set(l.strip() for l in f)
except FileNotFoundError as e:
  existing_index_jars = None

if existing_index_jars is not None:
  try:
    with open(sys.argv[2], "r") as f:
      for l in f:
        l = l.strip().split()
        # Load the existing index, removing any jars that are no longer to be indexed.
        retained_jars = set(j for j in l[2:] if j in jars_to_index)
        if len(retained_jars) != 0:
          index[l[0]] = retained_jars
  except FileNotFoundError:
    existing_index_jars = None
    index = dict()

if existing_index_jars is not None:
  new_jars = jars_to_index - existing_index_jars
  removed_jars = existing_index_jars - jars_to_index

  print("Updating the inverted index incrementally with %d jars added and %d jars removed" % (len(new_jars), len(removed_jars)), file = sys.stderr)
else:
  new_jars = jars_to_index
  removed_jars = set()

i = 0
for filename in new_jars:
  i += 1
  if i % 1000 == 0:
    print("Indexing ", i, filename, file = sys.stderr)
  try:
    index_file = filename
    cd_file = index_file[:-6] + ".cd"
    add_index(index_file, read_bytes(index_file) + read_bytes(cd_file))
  except Exception as e:
    print("Failed to read index from %s: %s" % (index_file, e), file = sys.stderr)

with open(sys.argv[2], "w") as f:
  for (package, files) in sorted(index.items(), key = lambda x: x[0]):
    print(package, len(files), " ".join(sorted(files)), file = f)

# Save the jar list used to create this index for future incremental updates.
shutil.copy(sys.argv[1], sys.argv[2] + ".input")
