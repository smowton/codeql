#!/usr/bin/env python3

import re
import sys

def upperFirst(string):
    return string[0].upper() + string[1:]

with open('../ql/src/config/semmlecode.dbscheme', 'r') as f:
    dbscheme = f.read()

# Remove comments
dbscheme = re.sub(r'/\*.*?\*/', '', dbscheme, flags=re.DOTALL)
dbscheme = re.sub(r'//[^\r\n]*/', '', dbscheme)

type_hierarchy = {}

with open('src/main/kotlin/KotlinExtractorDbScheme.kt', 'w') as kt:
    kt.write('/* Generated by ' + sys.argv[0] + ': Do not edit manually. */\n')
    kt.write('package com.github.codeql\n')

    kt.write('class Label<T>(val name: Int) {\n')
    kt.write('    override fun toString(): String = "#$name"\n')
    kt.write('}\n')


    # kind enums
    for name, kind, body in re.findall(r'case\s+@([^.\s]*)\.([^.\s]*)\s+of\b(.*?);',
                                       dbscheme,
                                       flags=re.DOTALL):
        for num, typ in re.findall(r'(\d+)\s*=\s*@(\S+)', body):
            s = type_hierarchy.get(typ, set())
            s.add(name)
            type_hierarchy[typ] = s

    # unions
    for name, unions in re.findall(r'@(\w+)\s*=\s*(@\w+(?:\s*\|\s*@\w+)*)',
                                       dbscheme,
                                       flags=re.DOTALL):
        type_hierarchy[name] = type_hierarchy.get(name, set())
        for typ in re.findall(r'@(\w+)', unions):
            s = type_hierarchy.get(typ, set())
            s.add(name)
            type_hierarchy[typ] = s

    # tables
    for relname, body in re.findall('\n([\w_]+)(\([^)]*\))',
                                    dbscheme,
                                    flags=re.DOTALL):
        for db_type in re.findall(':\s*@([^\s,]+)\s*(?:,|$)', body):
            type_hierarchy[db_type] = type_hierarchy.get(db_type, set())
        kt.write('fun TrapWriter.write' + upperFirst(relname) + '(')
        for colname, db_type in re.findall('(\S+)\s*:\s*([^\s,]+)', body):
            kt.write(colname + ': ')
            if db_type == 'int':
                # TODO: Do something better if the column is a 'case'
                kt.write('Int')
            elif db_type == 'float':
                kt.write('Double')
            elif db_type == 'string':
                kt.write('String')
            elif db_type == 'date':
                kt.write('String')
            elif db_type == 'boolean':
                kt.write('Boolean')
            elif db_type[0] == '@':
                kt.write('Label<out Db' + upperFirst(db_type[1:]) + '>')
            else:
                raise Exception('Bad db_type: ' + db_type)
            kt.write(', ')
        kt.write(') {\n')
        kt.write('    this.writeTrap("' + relname + '(')
        comma = ''
        for colname, db_type in re.findall('(\S+)\s*:\s*([^\s,]+)', body):
            kt.write(comma)
            if db_type == 'string' or db_type == 'date':
                kt.write('\\"$' + colname + '\\"') # TODO: Escaping
            else:
                # TODO: Any reformatting or escaping necessary?
                # e.g. float formats?
                kt.write('$' + colname)
            comma = ', '
        kt.write(')\\n")\n')
        kt.write('}\n')

    for typ in sorted(type_hierarchy):
        kt.write('sealed interface Db' + upperFirst(typ))
        names = sorted(type_hierarchy[typ])
        if names:
            kt.write(': ')
            kt.write(', '.join(map(lambda name: 'Db' + upperFirst(name), names)))
        kt.write(' {}\n')

