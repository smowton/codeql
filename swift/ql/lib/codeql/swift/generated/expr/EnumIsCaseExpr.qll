// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `EnumIsCaseExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.decl.EnumElementDecl
import codeql.swift.elements.expr.Expr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class EnumIsCaseExpr extends Synth::TEnumIsCaseExpr, Expr {
    override string getAPrimaryQlClass() { result = "EnumIsCaseExpr" }

    /**
     * Gets the sub expression of this enum is case expression.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Expr getImmediateSubExpr() {
      result =
        Synth::convertExprFromRaw(Synth::convertEnumIsCaseExprToRaw(this)
              .(Raw::EnumIsCaseExpr)
              .getSubExpr())
    }

    /**
     * Gets the sub expression of this enum is case expression.
     */
    final Expr getSubExpr() {
      exists(Expr immediate |
        immediate = this.getImmediateSubExpr() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }

    /**
     * Gets the element of this enum is case expression.
     */
    EnumElementDecl getElement() {
      result =
        Synth::convertEnumElementDeclFromRaw(Synth::convertEnumIsCaseExprToRaw(this)
              .(Raw::EnumIsCaseExpr)
              .getElement())
    }
  }
}
