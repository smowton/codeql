// generated by codegen/codegen.py, do not edit
/**
 * This module provides the generated definition of `SingleValueStmtExpr`.
 * INTERNAL: Do not import directly.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.expr.ExprImpl::Impl as ExprImpl
import codeql.swift.elements.stmt.Stmt

/**
 * INTERNAL: This module contains the fully generated definition of `SingleValueStmtExpr` and should not
 * be referenced directly.
 */
module Generated {
  /**
   * An expression that wraps a statement which produces a single value.
   * INTERNAL: Do not reference the `Generated::SingleValueStmtExpr` class directly.
   * Use the subclass `SingleValueStmtExpr`, where the following predicates are available.
   */
  class SingleValueStmtExpr extends Synth::TSingleValueStmtExpr, ExprImpl::Expr {
    override string getAPrimaryQlClass() { result = "SingleValueStmtExpr" }

    /**
     * Gets the statement of this single value statement expression.
     */
    Stmt getStmt() {
      result =
        Synth::convertStmtFromRaw(Synth::convertSingleValueStmtExprToRaw(this)
              .(Raw::SingleValueStmtExpr)
              .getStmt())
    }
  }
}
