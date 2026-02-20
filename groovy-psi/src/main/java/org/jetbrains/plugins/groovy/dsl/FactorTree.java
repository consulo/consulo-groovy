package org.jetbrains.plugins.groovy.dsl;

import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.language.psi.PsiElement;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.dataholder.UserDataHolderBase;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class FactorTree extends UserDataHolderBase {
  private static final Key<CachedValue<Map>> GDSL_MEMBER_CACHE = Key.create("GDSL_MEMBER_CACHE");
  private final CachedValueProvider<Map> myProvider;
  private final CachedValue<Map> myTopLevelCache;
  private final GroovyDslExecutor myExecutor;

  public FactorTree(final Project project, GroovyDslExecutor executor) {
    myExecutor = executor;
    myProvider = new CachedValueProvider<Map>() {
      @Nonnull
	  @Override
      public Result<Map> compute() {
        return new Result<Map>(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
      }
    };
    myTopLevelCache = CachedValuesManager.getManager(project).createCachedValue(myProvider, false);
  }

  public void cache(GroovyClassDescriptor descriptor, CustomMembersHolder holder) {
    Map current = null;
    for (Factor factor : descriptor.affectingFactors) {
      Object key;
      switch (factor) {
        case placeElement: key = descriptor.getPlace(); break;
        case placeFile: key = descriptor.getPlaceFile(); break;
        case qualifierType: key = descriptor.getTypeText(); break;
        default: throw new IllegalStateException("Unknown variant: "+ factor);
      }
      if (current == null) {
        if (key instanceof UserDataHolder) {
          Project project = descriptor.getProject();
          current = CachedValuesManager.getManager(project).getCachedValue((UserDataHolder)key, GDSL_MEMBER_CACHE, myProvider, false);
          continue;
        }

        current = myTopLevelCache.getValue();
      }
      Map next = (Map)current.get(key);
      if (next == null) {
        //noinspection unchecked
        current.put(key, next = new ConcurrentHashMap());
      }
      current = next;
    }

    if (current == null) current = myTopLevelCache.getValue();
    //noinspection unchecked
    current.put(myExecutor, holder);
  }

  @Nullable
  public CustomMembersHolder retrieve(PsiElement place, PsiFile placeFile, String qualifierType) {
    return retrieveImpl(place, placeFile, qualifierType, myTopLevelCache.getValue(), true);

  }

  @Nullable
  private CustomMembersHolder retrieveImpl(@Nonnull PsiElement place, @Nonnull PsiFile placeFile, @Nonnull String qualifierType, @Nullable Map current, boolean topLevel) {
    if (current == null) return null;

    CustomMembersHolder result;

    result = (CustomMembersHolder)current.get(myExecutor);
    if (result != null) return result;

    result = retrieveImpl(place, placeFile, qualifierType, (Map)current.get(qualifierType), false);
    if (result != null) return result;

    result = retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(placeFile, current, topLevel), false);
    if (result != null) return result;

    return retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(place, current, topLevel), false);
  }

  private static Map getFromMapOrUserData(UserDataHolder holder, Map map, boolean fromUserData) {
    if (fromUserData) {
      CachedValue<Map> cache = holder.getUserData(GDSL_MEMBER_CACHE);
      return cache != null && cache.hasUpToDateValue() ? cache.getValue() : null;
    }
    return (Map)map.get(holder);
  }
}
