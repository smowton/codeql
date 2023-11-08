// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `Accessor`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.decl.AccessorOrNamedFunction

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class Accessor extends Synth::TAccessor, AccessorOrNamedFunction {
    override string getAPrimaryQlClass() { result = "Accessor" }

    /**
     * Holds if this accessor is a getter.
     */
    predicate isGetter() { Synth::convertAccessorToRaw(this).(Raw::Accessor).isGetter() }

    /**
     * Holds if this accessor is a setter.
     */
    predicate isSetter() { Synth::convertAccessorToRaw(this).(Raw::Accessor).isSetter() }

    /**
     * Holds if this accessor is a `willSet`, called before the property is set.
     */
    predicate isWillSet() { Synth::convertAccessorToRaw(this).(Raw::Accessor).isWillSet() }

    /**
     * Holds if this accessor is a `didSet`, called after the property is set.
     */
    predicate isDidSet() { Synth::convertAccessorToRaw(this).(Raw::Accessor).isDidSet() }

    /**
     * Holds if this accessor is a `_read` coroutine, yielding a borrowed value of the property.
     */
    predicate isRead() { Synth::convertAccessorToRaw(this).(Raw::Accessor).isRead() }

    /**
     * Holds if this accessor is a `_modify` coroutine, yielding an inout value of the property.
     */
    predicate isModify() { Synth::convertAccessorToRaw(this).(Raw::Accessor).isModify() }

    /**
     * Holds if this accessor is an `unsafeAddress` immutable addressor.
     */
    predicate isUnsafeAddress() {
      Synth::convertAccessorToRaw(this).(Raw::Accessor).isUnsafeAddress()
    }

    /**
     * Holds if this accessor is an `unsafeMutableAddress` mutable addressor.
     */
    predicate isUnsafeMutableAddress() {
      Synth::convertAccessorToRaw(this).(Raw::Accessor).isUnsafeMutableAddress()
    }
  }
}
