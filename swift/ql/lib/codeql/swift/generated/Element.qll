// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `Element`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class Element extends Synth::TElement {
    /**
     * Gets the string representation of this element.
     */
    string toString() { none() } // overridden by subclasses

    /**
     * Gets the name of a primary CodeQL class to which this element belongs.
     *
     * This is the most precise syntactic category to which they belong; for
     * example, `CallExpr` is a primary class, but `ApplyExpr` is not.
     *
     * There might be some corner cases when this returns multiple classes, or none.
     */
    string getAPrimaryQlClass() { none() } // overridden by subclasses

    /**
     * Gets a comma-separated list of the names of the primary CodeQL classes to which this element belongs.
     */
    final string getPrimaryQlClasses() { result = concat(this.getAPrimaryQlClass(), ",") }

    /**
     * Gets the most immediate element that should substitute this element in the explicit AST, if any.
     * Classes can override this to indicate this node should be in the "hidden" AST, mostly reserved
     * for conversions and syntactic sugar nodes like parentheses.
     */
    Element getResolveStep() { none() } // overridden by subclasses

    /**
     * Gets the element that should substitute this element in the explicit AST, applying `getResolveStep`
     * transitively.
     */
    final Element resolve() {
      not exists(this.getResolveStep()) and result = this
      or
      result = this.getResolveStep().resolve()
    }

    /**
     * Holds if this element is unknown.
     */
    predicate isUnknown() { Synth::convertElementToRaw(this).isUnknown() }
  }
}
