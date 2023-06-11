#!/usr/bin/python

import sys
import os
import os.path
import struct
import subprocess
import concurrent.futures

from zipfile import ZipFile, ZipExtFile, sizeFileHeader, BadZipFile, _FH_SIGNATURE, structFileHeader, \
    stringFileHeader, _FH_FILENAME_LENGTH, _FH_EXTRA_FIELD_LENGTH, _FH_GENERAL_PURPOSE_FLAG_BITS, ZipInfo, \
    sizeEndCentDir, structEndArchive, _ECD_OFFSET, _ECD_SIZE, structEndArchive64, _CD64_DIRECTORY_SIZE, \
    _CD64_OFFSET_START_CENTDIR

if len(sys.argv) < 3:
  print("Usage: build-maven-index.py indexurl workingdir", file=sys.stderr)
  sys.exit(1)

rooturl = sys.argv[1]
if rooturl.endswith("/"):
  rooturl = rooturl[:-1]
indexurl = rooturl + "/.index/nexus-maven-repository-index.gz"
workingdir = sys.argv[2]
verbose = any(s == "--verbose" for s in sys.argv)

os.makedirs(workingdir, exist_ok = True)

script_dir = os.path.dirname(os.path.realpath(__file__))
index_reader_script = os.path.join(script_dir, "maven-index-reader", "run.sh")
pick_versions_script = os.path.join(script_dir, "pick-best-versions.py")

makefile = os.path.join(workingdir, "Makefile")

### Use make for the first few stages: get the Maven index, unpack and retrieve the latest versions of JARs listed in said index.

with open(makefile, "w") as f:
  f.write("all: jars-to-index.txt\n\n")
  f.write("indexer-cli-5.1.1.jar:\n\tcurl -L -O https://repo.maven.apache.org/maven2/org/apache/maven/indexer/indexer-cli/5.1.1/indexer-cli-5.1.1.jar\n\n")
  f.write("maven-index.gz:\n\tcurl -L -o maven-index.gz %s\n\n" % indexurl)
  f.write("maven-index-unpacked: nexus-maven-repository-index.gz indexer-cli-5.1.1.jar\n\tjava -jar indexer-cli-5.1.1.jar --unpack nexus-maven-repository-index.gz --destination maven-index-unpacked --type full\n\n")
  f.write("jars-to-index.txt: maven-index-unpacked\n\t%s maven-index-unpacked | python %s > jars-to-index.txt\n\n" % (index_reader_script, pick_versions_script))

subprocess.check_call(["make", "-C", workingdir, "-f", makefile])

### Now use curl to fetch any JAR indices we don't already have stored locally. We do this in three phases: using HEAD requests to get their lengths, fetching the zip central directories, then fetching the full zip index.

jar_indices_dir = os.path.join(workingdir, "jar-indices")
os.makedirs(jar_indices_dir, exist_ok = True)

curl_config = os.path.join(workingdir, "curl.config")

jars_to_fetch = []

with open(os.path.join(workingdir, "jars-to-index.txt"), "r") as f:
  for l in f:
    u_and_i = l.split()
    u_fields = u_and_i[0].split("|")
    groupid = u_fields[0]
    groupid_path = groupid.replace(".", "/")
    artifactid = u_fields[1]
    version = u_fields[2]
    classifier_suffix = "-" + u_fields[3] if len(u_fields) == 5 else ""
    relative_url = "%s/%s/%s/%s-%s%s.jar" % (groupid_path, artifactid, version, artifactid, version, classifier_suffix)
    url = rooturl + "/" + relative_url
    local_path = os.path.join(jar_indices_dir, relative_url)
    jars_to_fetch.append((url, local_path))

def escape_dquotes(s):
  return s.replace('"', '\\"')

def write_curl_config(curl_config, requests, get_additional_options):
  n_written = 0
  errors = []

  def eval_one_request(request):
    url, local_path = request
    if not os.path.exists(local_path):
      return (True, get_additional_options(url, local_path))
    return (False, None)
  
  with concurrent.futures.ThreadPoolExecutor() as executor:
    request_options = executor.map(eval_one_request, requests)
 
  with open(curl_config, "w") as curl_config_f:
    for ((url, local_path), needed_and_options) in zip(requests, request_options):
      needed, options = needed_and_options
      if needed:
        if options is None:
          errors.append((url, local_path))
          continue
        if n_written != 0:
          curl_config_f.write("--next\n")
        additional_options_text = "".join(opt + "\n" for opt in options)
        curl_config_f.write("%surl = \"%s\"\noutput = \"%s\"\n" % (additional_options_text, escape_dquotes(url), escape_dquotes(local_path)))
        os.makedirs(os.path.dirname(local_path), exist_ok = True)
        n_written += 1

  return n_written, errors

# Stage 1: HTTP HEAD all needed JARs

header_fetches = [(url, local_path + ".headers") for (url, local_path) in jars_to_fetch]
n_to_fetch, errors = write_curl_config(curl_config, header_fetches, lambda x, y: ("head",))
if n_to_fetch > 0:
  print("Fetching %d JAR headers" % n_to_fetch)
  subprocess.check_call(["curl", "-Z", "-K", curl_config])
else:
  print("Header fetches: all up to date")

# Stage 2: Fetch zip end-of-central-directory records (a fixed-length record at the end of the file)

contentlength = "content-length: "

def getlength(headerfile):
  with open(headerfile, "r") as f:
    for (i, l) in enumerate(f):
      if i == 0:
        bits = l.split()
        code = bits[1]
        if code != "200":
          if verbose:
            print("Header file %s has unexpected HTTP response %s" % (headerfile, l.strip()), file = sys.stderr)
          return None
      if l.startswith(contentlength):
        return int(l[len(contentlength) : ])
  return None

def get_cd_config(url, localpath):
  headersfile = localpath[:-3] + ".headers"
  try:
    length = getlength(headersfile)
  except Exception as e:
    if verbose:
      print("Failed to get length for %s: %s" % (headersfile, e), file = sys.stderr)
    length = None
  if length is None:
    return None
  return ("range = %d-%d" % (length - 22, length - 1),) # Size of a zip central directory

cd_fetches = [(url, local_path + ".cd") for (url, local_path) in jars_to_fetch]
n_to_fetch, errors = write_curl_config(curl_config, cd_fetches, get_cd_config)

if len(errors) > 0:
  print("Skipping fetching %d jar central directories" % len(errors), file = sys.stderr)
if n_to_fetch > 0:
  print("Fetching %d jar central directories" % n_to_fetch)
  subprocess.check_call(["curl", "-Z", "-K", curl_config])
else:
  print("Central directory fetches: all up to date")

# Stage 3: Fetch zip indices (a variable-length array of records usually immediately preceding the end-of-central-directory record)

def getcdcoords(cd):
  with open(cd, "rb") as f:
    bs = f.read()
  if bs[:2] != b'PK':
    raise Exception("Not a zip CD")
  endrec = struct.unpack(structEndArchive, bs)  
  return endrec[_ECD_OFFSET], endrec[_ECD_SIZE]

def get_index_config(url, localpath):
  cdfile = localpath[:-6] + ".cd"
  try:
    (start, size) = getcdcoords(cdfile)
  except Exception as e:
    if verbose:
      print("Failed to get central directory coordinates for %s: %s" % (cdfile, e), file = sys.stderr)
    (start, size) = (None, None)
  if start is None:
    return None
  return ("range = %d-%d" % (start, start + size - 1),) # Size of the zip index

index_fetches = [(url, local_path + ".index") for (url, local_path) in jars_to_fetch]
n_to_fetch, errors = write_curl_config(curl_config, index_fetches, get_index_config)

if len(errors) > 0:
  print("Skipping fetching %d jar indices" % len(errors), file = sys.stderr)
if n_to_fetch > 0:
  print("Fetching %d jar indices" % n_to_fetch)
  subprocess.check_call(["curl", "-Z", "-K", curl_config])
else:
  print("Index fetches: all up to date")

all_indices_file = os.path.join(workingdir, "all-indices.txt")
indices_not_fetched = set(local_path for (url, local_path) in errors)

if n_to_fetch > 0 or not os.path.exists(all_indices_file):
  with open(all_indices_file, "w") as f:
    for (url, local_path) in index_fetches:
      if local_path not in indices_not_fetched:
        f.write(local_path + "\n")

### All HTTP fetching complete -- now use make again for the last few stages: inverting the index, and selecting preferred packages.

inverted_index_file = os.path.join(workingdir, "inverted-index.txt")
package_index_file = os.path.join(workingdir, "package-preferred-jars.txt")
package_index_file_with_header = os.path.join(workingdir, "package-preferred-jar-index.txt")

invert_index_script = os.path.join(script_dir, "invert-package-index.py")
pick_jars_script = os.path.join(script_dir, "pick-best-jars.py")

with open(makefile, "w") as f:
  f.write("%s: %s\n\tpython %s %s %s\n\n" % (inverted_index_file, all_indices_file, invert_index_script, all_indices_file, inverted_index_file))
  f.write("%s: %s\n\tpython %s %s %s\n\n" % (package_index_file, inverted_index_file, pick_jars_script, inverted_index_file, package_index_file))
  f.write("%s: %s\n\techo '%s' > %s\n\tsed -E 's@([,=])%s@\\1@g' < %s >> %s" % (package_index_file_with_header, package_index_file, rooturl, package_index_file_with_header, jar_indices_dir, package_index_file, package_index_file_with_header))

subprocess.check_call(["make", "-C", workingdir, "-f", makefile, package_index_file_with_header])
