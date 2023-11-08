// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `OneWayExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.expr.Expr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class OneWayExpr extends Synth::TOneWayExpr, Expr {
    override string getAPrimaryQlClass() { result = "OneWayExpr" }

    /**
     * Gets the sub expression of this one way expression.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Expr getImmediateSubExpr() {
      result =
        Synth::convertExprFromRaw(Synth::convertOneWayExprToRaw(this).(Raw::OneWayExpr).getSubExpr())
    }

    /**
     * Gets the sub expression of this one way expression.
     */
    final Expr getSubExpr() {
      exists(Expr immediate |
        immediate = this.getImmediateSubExpr() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }
  }
}
