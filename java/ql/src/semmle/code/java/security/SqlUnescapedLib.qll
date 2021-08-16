/* Definitions used by `SqlUnescaped.ql`. */
import semmle.code.java.security.ControlledString
import semmle.code.java.dataflow.TaintTracking

/**
 * A string concatenation that includes a string
 * not known to be programmer controlled.
 */
predicate builtFromUncontrolledConcat(
  Expr expr, Expr uncontrolled, Expr quote, Expr contribUncontrolled
) {
  // Base case
  exists(AddExpr concatExpr | concatExpr = expr |
    quote = concatExpr.getLeftOperand() and
    endsInQuote(quote) and
    uncontrolled = concatExpr.getRightOperand() and
    not controlledString(uncontrolled) and
    not controlledString(contribUncontrolled) and
    controlledStringProp*(contribUncontrolled, uncontrolled)
  )
  or
  // Recursive cases
  exists(Expr other | builtFromUncontrolledConcat(other, uncontrolled, quote, contribUncontrolled) |
    expr.(AddExpr).getAnOperand() = other
    or
    exists(Variable var | var.getAnAssignedValue() = other and var.getAnAccess() = expr)
  )
}

/**
 * A query built with a StringBuilder, where one of the
 * items appended is uncontrolled.
 */
predicate uncontrolledStringBuilderQuery(
  StringBuilderVar sbv, Expr uncontrolled, Expr quote, Expr contribUncontrolled
) {
  // A single append that has a problematic concatenation.
  exists(MethodAccess append |
    append = sbv.getAnAppend() and
    builtFromUncontrolledConcat(append.getArgument(0), uncontrolled, quote, contribUncontrolled)
  )
  or
  // Two calls to append, one ending in a quote, the next being uncontrolled.
  exists(MethodAccess quoteAppend, MethodAccess uncontrolledAppend |
    sbv.getAnAppend() = quoteAppend and
    quote = quoteAppend.getArgument(0) and
    endsInQuote(quote) and
    sbv.getNextAppend(quoteAppend) = uncontrolledAppend and
    uncontrolled = uncontrolledAppend.getArgument(0) and
    not controlledString(uncontrolled) and
    not controlledString(contribUncontrolled) and
    controlledStringProp*(contribUncontrolled, uncontrolled)
  )
}
