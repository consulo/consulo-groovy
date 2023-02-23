/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.PsiType;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveCache;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
public interface InferenceContext {
  InferenceContext TOP_CONTEXT = new InferenceContext() {
    @Nullable
    @Override
    public PsiType getVariableType(@Nonnull GrReferenceExpression ref) {
      return TypeInferenceHelper.getInferredType(ref);
    }

    @Override
    public <T> T getCachedValue(@Nonnull GroovyPsiElement element, @Nonnull final Supplier<T> computable) {
      CachedValuesManager manager = CachedValuesManager.getManager(element.getProject());
      Key<CachedValue<T>> key = manager.getKeyForClass(computable.getClass());
      return manager.getCachedValue(element, key, new CachedValueProvider<T>() {
        @Nullable
        @Override
        public Result<T> compute() {
          return Result.create(computable.get(), PsiModificationTracker.MODIFICATION_COUNT);
        }
      }, false);
    }

    @Override
    public <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@Nonnull T ref,
                                                                                  boolean incomplete,
                                                                                  ResolveCache.PolyVariantResolver<T> resolver) {
      ResolveResult[] results = ResolveCache.getInstance(ref.getElement().getProject()).resolveWithCaching(ref, resolver, true, incomplete);
      return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
    }

    @Nullable
    @Override
    public <T extends GroovyPsiElement> PsiType getExpressionType(@Nonnull T element, @Nonnull Function<T, PsiType> calculator) {
      return GroovyPsiManager.getInstance(element.getProject()).getType(element, calculator);
    }
  };

  @Nullable
  PsiType getVariableType(@Nonnull GrReferenceExpression ref);

  <T> T getCachedValue(@Nonnull GroovyPsiElement element, @Nonnull Supplier<T> computable);

  <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@Nonnull T ref,
                                                                         boolean incomplete,
                                                                         ResolveCache.PolyVariantResolver<T> resolver);

  @Nullable
  <T extends GroovyPsiElement> PsiType getExpressionType(@Nonnull T element, @Nonnull Function<T, PsiType> calculator);

  class PartialContext implements InferenceContext {
    private final Map<String, PsiType> myTypes;
    private final Map<PsiElement, Map<Object, Object>> myCache = new HashMap<>();

    public PartialContext(@Nonnull Map<String, PsiType> types) {
      myTypes = types;
    }

    @Nullable
    @Override
    public PsiType getVariableType(@Nonnull GrReferenceExpression ref) {
      return myTypes.get(ref.getReferenceName());
    }

    @Override
    public <T> T getCachedValue(@Nonnull GroovyPsiElement element, @Nonnull Supplier<T> computable) {
      return _getCachedValue(element, computable, computable.getClass());
    }

    private <T> T _getCachedValue(@Nullable PsiElement element, @Nonnull Supplier<T> computable, @Nonnull Object key) {
      Map<Object, Object> map = myCache.get(element);
      if (map == null) {
        myCache.put(element, map = new HashMap<>());
      }
      if (map.containsKey(key)) {
        //noinspection unchecked
        return (T)map.get(key);
      }

      T result = computable.get();
      map.put(key, result);
      return result;
    }

    @Nonnull
    @Override
    public <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@Nonnull final T ref,
                                                                                  final boolean incomplete,
                                                                                  @Nonnull final ResolveCache.PolyVariantResolver<T> resolver) {
      return _getCachedValue(ref.getElement(),
                             () -> (GroovyResolveResult[])resolver.resolve(ref, incomplete), Pair.create(incomplete, resolver.getClass()));
    }

    @Nullable
    @Override
    public <T extends GroovyPsiElement> PsiType getExpressionType(@Nonnull final T element,
                                                                  @Nonnull final Function<T, PsiType> calculator) {
      return _getCachedValue(element, () -> {
        PsiType type = calculator.apply(element);
        return type == PsiType.NULL ? null : type;
      }, "type");
    }
  }

}
