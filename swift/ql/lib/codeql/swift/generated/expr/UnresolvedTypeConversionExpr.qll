// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `UnresolvedTypeConversionExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.ErrorElement
import codeql.swift.elements.expr.ImplicitConversionExpr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class UnresolvedTypeConversionExpr extends Synth::TUnresolvedTypeConversionExpr,
    ImplicitConversionExpr, ErrorElement
  {
    override string getAPrimaryQlClass() { result = "UnresolvedTypeConversionExpr" }
  }
}
