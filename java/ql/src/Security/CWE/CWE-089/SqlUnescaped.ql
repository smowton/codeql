/**
 * @name Query built without neutralizing special characters
 * @description Building a SQL or Java Persistence query without escaping or otherwise neutralizing any special
 *              characters is vulnerable to insertion of malicious code.
 * @kind problem
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
      uncontrolledStringBuilderQuery(sbv, _, _, _) and
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

from QueryInjectionSink query, Expr uncontrolled, Expr endsInQuote, Expr contributor
where
  (
    builtFromUncontrolledConcat(query.asExpr(), uncontrolled, endsInQuote, contributor)
    or
    exists(StringBuilderVar sbv, UncontrolledStringBuilderSourceFlowConfig conf |
      uncontrolledStringBuilderQuery(sbv, uncontrolled, endsInQuote, contributor) and
      conf.hasFlow(DataFlow::exprNode(sbv.getToStringCall()), query)
    )
  ) and
  not queryTaintedBy(query, _, _)
select uncontrolled,
  "Possibly user-controlled expression (flows from $@, appears to be quoted by $@) may be used to build $@ without neutralizing special characters.",
  contributor, "this expression", endsInQuote, "this string", query, "this query"
