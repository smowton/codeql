// generated by codegen, do not edit
import codeql.rust.elements
import TestUtils

from SlicePat x, int getNumberOfPrefixes, string hasSlice, int getNumberOfSuffixes
where
  toBeTested(x) and
  not x.isUnknown() and
  getNumberOfPrefixes = x.getNumberOfPrefixes() and
  (if x.hasSlice() then hasSlice = "yes" else hasSlice = "no") and
  getNumberOfSuffixes = x.getNumberOfSuffixes()
select x, "getNumberOfPrefixes:", getNumberOfPrefixes, "hasSlice:", hasSlice,
  "getNumberOfSuffixes:", getNumberOfSuffixes
