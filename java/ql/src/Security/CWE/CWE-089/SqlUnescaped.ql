/**
 * @name Query built without neutralizing special characters
 * @description Building a SQL or Java Persistence query without escaping or otherwise neutralizing any special
 *              characters is vulnerable to insertion of malicious code.
 * @kind path-problem
 * @problem.severity error
 * @security-severity 8.8
 * @precision high
 * @id java/concatenated-sql-query
 * @tags security
 *       external/cwe/cwe-089
 *       external/cwe/cwe-564
 */

import java
import semmle.code.java.security.SqlUnescapedLib
import SqlInjectionLib

class UncontrolledStringBuilderSource extends DataFlow::ExprNode {
  UncontrolledStringBuilderSource() {
    exists(StringBuilderVar sbv |
      uncontrolledStringBuilderQuery(sbv, _) and
      this.getExpr() = sbv.getToStringCall()
    )
  }
}

class UncontrolledStringBuilderSourceFlowConfig extends TaintTracking::Configuration {
  UncontrolledStringBuilderSourceFlowConfig() {
    this = "SqlUnescaped::UncontrolledStringBuilderSourceFlowConfig"
  }

  override predicate isSource(DataFlow::Node src) { src instanceof UncontrolledStringBuilderSource }

  override predicate isSink(DataFlow::Node sink) { sink instanceof QueryInjectionSink }

  override predicate isSanitizer(DataFlow::Node node) {
    node.getType() instanceof PrimitiveType or node.getType() instanceof BoxedType
  }
}

predicate mayBeUncontrolled(QueryInjectionSink query, Expr uncontrolled, Expr uncontrolledSource) {
  (
    builtFromUncontrolledConcat(query.asExpr(), uncontrolled)
    or
    exists(StringBuilderVar sbv, UncontrolledStringBuilderSourceFlowConfig conf |
      uncontrolledStringBuilderQuery(sbv, uncontrolled) and
      conf.hasFlow(DataFlow::exprNode(sbv.getToStringCall()), query)
    )
  ) and
  not queryTaintedBy(query, _, _) and
  controlledStringProp*(uncontrolledSource, uncontrolled) and
  not controlledString(uncontrolledSource)
}

from QueryInjectionSink query, Expr uncontrolled, Expr uncontrolledSource
where mayBeUncontrolled(query, uncontrolled, uncontrolledSource)
select uncontrolled, uncontrolledSource, uncontrolled,
  "Possibly-user-controlled expression may be used in $@ without first neutralizing special characters.",
  query, "this query"

/** Holds if `(a,b)` is an edge in the graph of data flow path explanations. */
query predicate edges(Expr a, Expr b) { controlledStringProp(a, b) }

/** Holds if `n` is a node in the graph of data flow path explanations. */
query predicate nodes(Expr n, string key, string val) {
  (
    mayBeUncontrolled(_, n, _) or
    controlledStringProp(n, _) or
    controlledStringProp(_, n)
  ) and
  key = "semmle.label" and
  val = n.toString()
}
