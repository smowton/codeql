// generated by codegen, remove this comment if you wish to edit this file
/**
 * This module provides a hand-modifiable wrapper around the generated class `Unextracted`.
 *
 * INTERNAL: Do not use.
 */

private import codeql.rust.internal.generated.Unextracted

/**
 * INTERNAL: This module contains the customizable definition of `Unextracted` and should not
 * be referenced directly.
 */
module Impl {
  /**
   * The base class marking everything that was not properly extracted for some reason, such as:
   * * syntax errors
   * * insufficient context information
   * * yet unimplemented parts of the extractor
   */
  class Unextracted extends Generated::Unextracted { }
}
