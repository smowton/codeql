import java

module SpringCache {
  private class FlowSummaries extends SummaryModelCsv {
    override predicate row(string row) {
      row = [
        "org.springframework.cache.Cache.ValueRetrievalException;false;getKey;;;MapKey of Argument[-1];ReturnValue;value",
        "org.springframework.cache.Cache.ValueWrapper;true;get;;;MapValue of Argument[-1];ReturnValue;value",
        "org.springframework.cache.Cache;true;get;(Object);;MapValue of Argument[-1];MapValue of ReturnValue;value",
        "org.springframework.cache.Cache;true;get;(Object, Callable<T>);;MapValue of Argument[-1];ReturnValue;value",
        "org.springframework.cache.Cache;true;get;(Object, Class<T>);;MapValue of Argument[-1];ReturnValue;value",
        // note for review: there is no guarantee that the result is a map, but all implementations seem to be
        "org.springframework.cache.Cache;true;getNativeCache;;;MapKey of Argument[-1];MapKey of ReturnValue;value",
        "org.springframework.cache.Cache;true;getNativeCache;;;MapValue of Argument[-1];MapValue of ReturnValue;value",
        "org.springframework.cache.Cache;true;put;;;Argument[0];MapKey of Argument[-1];value",
        "org.springframework.cache.Cache;true;put;;;Argument[1];MapValue of Argument[-1];value",
        "org.springframework.cache.Cache;true;putIfAbsent;;;Argument[0];MapKey of Argument[-1];value",
        "org.springframework.cache.Cache;true;putIfAbsent;;;Argument[1];MapValue of Argument[-1];value",
        "org.springframework.cache.Cache;true;putIfAbsent;;;MapValue of Argument[-1];ReturnValue;value",
      ]
    }
  }
}
