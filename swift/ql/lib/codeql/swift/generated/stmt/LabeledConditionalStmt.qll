// generated by codegen/codegen.py
/**
 * This module provides the generated definition of `LabeledConditionalStmt`.
 * INTERNAL: Do not use. Import the corresponding module under `elements` instead.
 */

private import codeql.swift.generated.Synth
private import codeql.swift.generated.Raw
import codeql.swift.elements.stmt.LabeledStmt
import codeql.swift.elements.stmt.StmtCondition

module Generated {
  /**
   * INTERNAL: Do not use. Use the wrapper class under `elements` instead.
   */
  class LabeledConditionalStmt extends Synth::TLabeledConditionalStmt, LabeledStmt {
    /**
     * Gets the condition of this labeled conditional statement.
     */
    StmtCondition getCondition() {
      result =
        Synth::convertStmtConditionFromRaw(Synth::convertLabeledConditionalStmtToRaw(this)
              .(Raw::LabeledConditionalStmt)
              .getCondition())
    }
  }
}
