// generated by codegen/codegen.py, do not edit
/**
 * This module provides the generated definition of `OpenedArchetypeType`.
 * INTERNAL: Do not import directly.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.type.LocalArchetypeTypeImpl::Impl as LocalArchetypeTypeImpl

/**
 * INTERNAL: This module contains the fully generated definition of `OpenedArchetypeType` and should not
 * be referenced directly.
 */
module Generated {
  /**
   * INTERNAL: Do not reference the `Generated::OpenedArchetypeType` class directly.
   * Use the subclass `OpenedArchetypeType`, where the following predicates are available.
   */
  class OpenedArchetypeType extends Synth::TOpenedArchetypeType,
    LocalArchetypeTypeImpl::LocalArchetypeType
  {
    override string getAPrimaryQlClass() { result = "OpenedArchetypeType" }
  }
}
