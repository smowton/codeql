/**
 * @name Query built from user-controlled sources
 * @description Building a SQL or Java Persistence query from user-controlled sources is vulnerable to insertion of
 *              malicious code by the user.
 * @kind problem
 * @problem.severity error
 * @security-severity 8.8
 * @precision high
 * @id meta/advice/java/sql-injection
 * @tags security
 *       external/cwe/cwe-089
 *       external/cwe/cwe-564
 */

import java
import semmle.code.java.security.SqlInjectionQuery

from QueryInjectionSink query, MethodCall mc, Callable target
where
  queryIsTaintedBy(query, _, _) and
  mc.getEnclosingCallable().getDeclaringType() = query.asExpr().getEnclosingCallable().getDeclaringType() and
  mc.getCallee() = target and
  target.fromSource()
select query, "Call to $@ targets method $@", mc, mc.toString(), target, target.toString()
