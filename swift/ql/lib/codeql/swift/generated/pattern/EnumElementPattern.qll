// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `EnumElementPattern`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.decl.EnumElementDecl
import codeql.swift.elements.pattern.Pattern

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class EnumElementPattern extends Synth::TEnumElementPattern, Pattern {
    override string getAPrimaryQlClass() { result = "EnumElementPattern" }

    /**
     * Gets the element of this enum element pattern.
     */
    EnumElementDecl getElement() {
      result =
        Synth::convertEnumElementDeclFromRaw(Synth::convertEnumElementPatternToRaw(this)
              .(Raw::EnumElementPattern)
              .getElement())
    }

    /**
     * Gets the sub pattern of this enum element pattern, if it exists.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Pattern getImmediateSubPattern() {
      result =
        Synth::convertPatternFromRaw(Synth::convertEnumElementPatternToRaw(this)
              .(Raw::EnumElementPattern)
              .getSubPattern())
    }

    /**
     * Gets the sub pattern of this enum element pattern, if it exists.
     */
    final Pattern getSubPattern() {
      exists(Pattern immediate |
        immediate = this.getImmediateSubPattern() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }

    /**
     * Holds if `getSubPattern()` exists.
     */
    final predicate hasSubPattern() { exists(this.getSubPattern()) }
  }
}
