// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `UnresolvedMemberExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.ErrorElement
import codeql.swift.elements.expr.Expr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class UnresolvedMemberExpr extends Synth::TUnresolvedMemberExpr, Expr, ErrorElement {
    override string getAPrimaryQlClass() { result = "UnresolvedMemberExpr" }

    /**
     * Gets the name of this unresolved member expression.
     */
    string getName() {
      result = Synth::convertUnresolvedMemberExprToRaw(this).(Raw::UnresolvedMemberExpr).getName()
    }
  }
}
