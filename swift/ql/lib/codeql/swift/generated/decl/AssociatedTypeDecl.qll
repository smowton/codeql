// generated by codegen/codegen.py, do not edit
/**
 * This module provides the generated definition of `AssociatedTypeDecl`.
 * INTERNAL: Do not import directly.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.decl.AbstractTypeParamDeclImpl::Impl as AbstractTypeParamDeclImpl

/**
 * INTERNAL: This module contains the fully generated definition of `AssociatedTypeDecl` and should not
 * be referenced directly.
 */
module Generated {
  /**
   * INTERNAL: Do not reference the `Generated::AssociatedTypeDecl` class directly.
   * Use the subclass `AssociatedTypeDecl`, where the following predicates are available.
   */
  class AssociatedTypeDecl extends Synth::TAssociatedTypeDecl,
    AbstractTypeParamDeclImpl::AbstractTypeParamDecl
  {
    override string getAPrimaryQlClass() { result = "AssociatedTypeDecl" }
  }
}
