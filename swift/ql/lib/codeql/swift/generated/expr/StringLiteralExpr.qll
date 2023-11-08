// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `StringLiteralExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.expr.BuiltinLiteralExpr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class StringLiteralExpr extends Synth::TStringLiteralExpr, BuiltinLiteralExpr {
    override string getAPrimaryQlClass() { result = "StringLiteralExpr" }

    /**
     * Gets the value of this string literal expression.
     */
    string getValue() {
      result = Synth::convertStringLiteralExprToRaw(this).(Raw::StringLiteralExpr).getValue()
    }
  }
}
