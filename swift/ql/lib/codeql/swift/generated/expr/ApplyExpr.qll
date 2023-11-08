// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `ApplyExpr`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.expr.Argument
import codeql.swift.elements.expr.Expr

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class ApplyExpr extends Synth::TApplyExpr, Expr {
    /**
     * Gets the function being applied.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Expr getImmediateFunction() {
      result =
        Synth::convertExprFromRaw(Synth::convertApplyExprToRaw(this).(Raw::ApplyExpr).getFunction())
    }

    /**
     * Gets the function being applied.
     */
    final Expr getFunction() {
      exists(Expr immediate |
        immediate = this.getImmediateFunction() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }

    /**
     * Gets the `index`th argument passed to the applied function (0-based).
     */
    Argument getArgument(int index) {
      result =
        Synth::convertArgumentFromRaw(Synth::convertApplyExprToRaw(this)
              .(Raw::ApplyExpr)
              .getArgument(index))
    }

    /**
     * Gets any of the arguments passed to the applied function.
     */
    final Argument getAnArgument() { result = this.getArgument(_) }

    /**
     * Gets the number of arguments passed to the applied function.
     */
    final int getNumberOfArguments() { result = count(int i | exists(this.getArgument(i))) }
  }
}
