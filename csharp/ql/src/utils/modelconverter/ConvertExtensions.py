# Tool to generate data extensions files based on the existing models.
# Usage:
# python3 ConvertExtensions.py
# (1) A folder named `csharp/ql/lib/ext` will be created, if it doesn't already exist.
# (2) The converted models will be written to `csharp/ql/lib/ext`. One file for each namespace.

import os
import subprocess
import sys

# Add Models as Data script directory to sys.path.
gitroot = subprocess.check_output(["git", "rev-parse", "--show-toplevel"]).decode("utf-8").strip()
madpath = os.path.join(gitroot, "misc/scripts/models-as-data/")
sys.path.append(madpath)

import helpers
import convert_extensions as extensions

print('Running script to generate data extensions files from the existing MaD models.')
print('Making a dummy database.')

# Configuration
language = "csharp"
dbDir = "db"

helpers.run_cmd(['codeql', 'database', 'create', f'--language={language}', '-c', 'dotnet clean project/', '-c', 'dotnet build project/', dbDir])

print('Converting data extensions for C#.')
extensions.Converter(language, dbDir).run()

print('Cleanup.')
# Cleanup - delete database.
helpers.remove_dir(dbDir)
print('Done.')