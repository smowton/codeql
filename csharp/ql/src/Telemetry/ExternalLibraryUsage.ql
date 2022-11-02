/**
 * @name External libraries
 * @description A list of external libraries used in the code given by their namespace.
 * @kind metric
 * @tags summary telemetry
 * @id csharp/telemetry/external-libs
 */

private import csharp
private import semmle.code.csharp.dispatch.Dispatch
private import ExternalApi

private predicate getRelevantUsages(string namespace, int usages) {
  usages =
    strictcount(DispatchCall c, ExternalApi api |
      c = api.getACall() and
      api.getNamespace() = namespace and
      not api.isUninteresting()
    )
}

private int getOrder(string namespace) {
  namespace =
    rank[result](string i, int usages | getRelevantUsages(i, usages) | i order by usages desc, i)
}

from ExternalApi api, string namespace, int usages
where
  namespace = api.getNamespace() and
  getRelevantUsages(namespace, usages) and
  getOrder(namespace) <= resultLimit()
select namespace, usages order by usages desc
