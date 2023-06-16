import re

private_class_regex = re.compile(".*\\$[0-9]+\\.class$")

def is_private(classfile):
  # Since we only have the index, not the class file headers, we have to underapproximate private classes.
  # Here the best we can currently do is recognise anonymous classes like MyClass$1.class.
  return private_class_regex.match(classfile) is not None
