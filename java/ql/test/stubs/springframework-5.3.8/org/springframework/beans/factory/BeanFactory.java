package org.springframework.beans.factory;

public interface BeanFactory {

  public <T> T getBean(Class<T> requiredType);

}
