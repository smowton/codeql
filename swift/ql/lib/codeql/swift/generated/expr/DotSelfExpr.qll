// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `DotSelfExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.expr.IdentityExpr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class DotSelfExpr extends Synth::TDotSelfExpr, IdentityExpr {
    override string getAPrimaryQlClass() { result = "DotSelfExpr" }
  }
}
