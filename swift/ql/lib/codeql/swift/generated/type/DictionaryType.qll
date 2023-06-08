// generated by codegen/codegen.py
private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.type.SyntaxSugarType
import codeql.swift.elements.type.Type

module Generated {
  class DictionaryType extends Synth::TDictionaryType, SyntaxSugarType {
    override string getAPrimaryQlClass() { result = "DictionaryType" }

    /**
     * Gets the key type of this dictionary type.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Type getImmediateKeyType() {
      result =
        Synth::convertTypeFromRaw(Synth::convertDictionaryTypeToRaw(this)
              .(Raw::DictionaryType)
              .getKeyType())
    }

    /**
     * Gets the key type of this dictionary type.
     */
    final Type getKeyType() {
      exists(Type immediate |
        immediate = this.getImmediateKeyType() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }

    /**
     * Gets the value type of this dictionary type.
     *
     * This includes nodes from the "hidden" AST. It can be overridden in subclasses to change the
     * behavior of both the `Immediate` and non-`Immediate` versions.
     */
    Type getImmediateValueType() {
      result =
        Synth::convertTypeFromRaw(Synth::convertDictionaryTypeToRaw(this)
              .(Raw::DictionaryType)
              .getValueType())
    }

    /**
     * Gets the value type of this dictionary type.
     */
    final Type getValueType() {
      exists(Type immediate |
        immediate = this.getImmediateValueType() and
        if exists(this.getResolveStep()) then result = immediate else result = immediate.resolve()
      )
    }
  }
}
