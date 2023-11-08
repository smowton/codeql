// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `AssignExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.expr.Expr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class AssignExpr extends Synth::TAssignExpr, Expr {
    override string getAPrimaryQlClass() { result = "AssignExpr" }

    /**
     * Gets the dest of this assign expression.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Expr getImmediateDest() {
      result =
        Synth::convertExprFromRaw(Synth::convertAssignExprToRaw(this).(Raw::AssignExpr).getDest())
    }

    /**
     * Gets the dest of this assign expression.
     */
    final Expr getDest() {
      exists(Expr immediate |
        immediate = this.getImmediateDest() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }

    /**
     * Gets the source of this assign expression.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Expr getImmediateSource() {
      result =
        Synth::convertExprFromRaw(Synth::convertAssignExprToRaw(this).(Raw::AssignExpr).getSource())
    }

    /**
     * Gets the source of this assign expression.
     */
    final Expr getSource() {
      exists(Expr immediate |
        immediate = this.getImmediateSource() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }
  }
}
