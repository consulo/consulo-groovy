package org.jetbrains.plugins.groovy.util;

import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.UserDataHolderEx;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class LightCacheKey<T> {

  private final Key<Pair<Long, T>> key = Key.create(this.toString());

  /**
   * @return Cached value or null if cached value is not exists or outdated.
   */
  @Nullable
  public T getCachedValue(PsiElement holder) {
    Pair<Long, T> userData = holder.getUserData(key);

    if (userData == null || getModificationCount(holder) != userData.first) {
      return null;
    }

    return userData.second;
  }

  protected long getModificationCount(PsiElement holder) {
    return holder.getManager().getModificationTracker().getModificationCount();
  }

  public T putCachedValue(PsiElement holder, @Nonnull T value) {
    long modificationCount = getModificationCount(holder);

    Pair<Long, T> pair = Pair.create(modificationCount, value);

    Pair<Long, T> puttedValue = ((UserDataHolderEx)holder).putUserDataIfAbsent(key, pair);
    if (puttedValue == pair) {
      return value;
    }

    if (puttedValue.first == modificationCount) {
      return puttedValue.second;
    }

    if (((UserDataHolderEx)holder).replace(key, puttedValue, pair)) {
      return value;
    }

    Pair<Long, T> createdFromOtherThreadValue = holder.getUserData(key);
    //noinspection ConstantConditions
    assert createdFromOtherThreadValue.first == modificationCount;

    return createdFromOtherThreadValue.second;
  }

  public static <T> LightCacheKey<T> create() {
    return new LightCacheKey<T>();
  }

  public static <T> LightCacheKey<T> createByJavaModificationCount() {
    return new LightCacheKey<T>() {
      @Override
      protected long getModificationCount(PsiElement holder) {
        return holder.getManager().getModificationTracker().getJavaStructureModificationCount();
      }
    };
  }

  public static <T> LightCacheKey<T> createByFileModificationCount() {
    return new LightCacheKey<T>() {
      @Override
      protected long getModificationCount(PsiElement holder) {
        return holder.getContainingFile().getModificationStamp();
      }
    };
  }

}
