// generated by codegen/codegen.py, do not edit
/**
 * This module provides the generated definition of `PackElementType`.
 * INTERNAL: Do not import directly.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.type.Type
import codeql.swift.elements.type.TypeImpl::Impl as TypeImpl

/**
 * INTERNAL: This module contains the fully generated definition of `PackElementType` and should not
 * be referenced directly.
 */
module Generated {
  /**
   * A type of PackElementExpr, see PackElementExpr for more information.
   * INTERNAL: Do not reference the `Generated::PackElementType` class directly.
   * Use the subclass `PackElementType`, where the following predicates are available.
   */
  class PackElementType extends Synth::TPackElementType, TypeImpl::Type {
    override string getAPrimaryQlClass() { result = "PackElementType" }

    /**
     * Gets the pack type of this pack element type.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Type getImmediatePackType() {
      result =
        Synth::convertTypeFromRaw(Synth::convertPackElementTypeToRaw(this)
              .(Raw::PackElementType)
              .getPackType())
    }

    /**
     * Gets the pack type of this pack element type.
     */
    final Type getPackType() {
      exists(Type immediate |
        immediate = this.getImmediatePackType() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }
  }
}
