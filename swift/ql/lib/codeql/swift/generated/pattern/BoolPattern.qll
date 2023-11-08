// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `BoolPattern`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.pattern.Pattern

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class BoolPattern extends Synth::TBoolPattern, Pattern {
    override string getAPrimaryQlClass() { result = "BoolPattern" }

    /**
     * Gets the value of this bool pattern.
     */
    boolean getValue() {
      result = Synth::convertBoolPatternToRaw(this).(Raw::BoolPattern).getValue()
    }
  }
}
