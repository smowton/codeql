// generated by codegen, remove this comment if you wish to edit this file
/**
 * This module provides a hand-modifiable wrapper around the generated class `ClosureExpr`.
 *
 * INTERNAL: Do not use.
 */

private import codeql.rust.internal.generated.ClosureExpr

/**
 * INTERNAL: This module contains the customizable definition of `ClosureExpr` and should not
 * be referenced directly.
 */
module Impl {
  /**
   * A closure expression. For example:
   * ```
   * |x| x + 1;
   * move |x: i32| -> i32 { x + 1 };
   * async |x: i32, y| x + y;
   *  #[coroutine]
   * |x| yield x;
   *  #[coroutine]
   *  static |x| yield x;
   * ```
   */
  class ClosureExpr extends Generated::ClosureExpr { }
}
