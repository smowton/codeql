/**
 * https://www.zlib.net/
 */

import cpp
import semmle.code.cpp.ir.dataflow.TaintTracking
import semmle.code.cpp.security.FlowSources
import DecompressionBomb

/**
 * The `inflate` and `inflateSync` functions are used in flow sink.
 *
 * `inflate(z_stream strm, int flush)`
 *
 * `inflateSync(z_stream strm)`
 */
class InflateFunction extends DecompressionFunction {
  InflateFunction() { this.hasGlobalName(["inflate", "inflateSync"]) }

  override int getArchiveParameterIndex() { result = 0 }
}

/**
 * The `next_in` member of a `z_stream` variable is used in flow steps.
 */
predicate nextInAdditionalFlowStep(DataFlow::Node node1, DataFlow::Node node2) {
  exists(Variable nextInVar, VariableAccess zStreamAccess |
    nextInVar.getDeclaringType().hasName("z_stream") and
    nextInVar.hasName("next_in") and
    zStreamAccess.getType().hasName("z_stream")
  |
    nextInVar.getAnAccess().getQualifier().(VariableAccess).getTarget() = zStreamAccess.getTarget() and
    node1.asIndirectExpr() = nextInVar.getAnAssignedValue() and
    node2.asExpr() = zStreamAccess
  )
}
