package org.jetbrains.plugins.groovy.util;

import jakarta.annotation.Nonnull;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Sergey Evdokimov
 */
public class ClassInstanceCache {

  private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

  private ClassInstanceCache() {
  }

  @SuppressWarnings("unchecked")
  public static <T> T getInstance(@Nonnull String className, ClassLoader classLoader) {
    Object res = CACHE.get(className);
    if (res == null) {
      try {
        res = classLoader.loadClass(className).newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      Object oldValue = CACHE.putIfAbsent(className, res);
      if (oldValue != null) {
        res = oldValue;
      }
    }

    return (T)res;
  }
}
