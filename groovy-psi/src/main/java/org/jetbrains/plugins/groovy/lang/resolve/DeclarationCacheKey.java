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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.java.language.psi.PsiType;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.function.PairProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
* @author peter
*/
class DeclarationCacheKey {
  private static final CachedValueProvider<ConcurrentMap<DeclarationCacheKey, List<DeclarationHolder>>> VALUE_PROVIDER =
    new CachedValueProvider<ConcurrentMap<DeclarationCacheKey, List<DeclarationHolder>>>() {
      @Nullable
      @Override
      public Result<ConcurrentMap<DeclarationCacheKey, List<DeclarationHolder>>> compute() {
        ConcurrentMap<DeclarationCacheKey, List<DeclarationHolder>> map = ContainerUtil.newConcurrentMap();
        return Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
      }
    };
  @Nullable
  private final String name;
  @Nonnull
  private final EnumSet<ClassHint.ResolveKind> kinds;
  private final boolean nonCode;
  @Nonnull
  private final PsiElement place;

  DeclarationCacheKey(@Nullable String name, ClassHint hint, boolean nonCode, @Nonnull PsiElement place) {
    this.name = name;
    this.kinds = getResolveKinds(hint);
    this.nonCode = nonCode;
    this.place = place;
  }

  private static EnumSet<ClassHint.ResolveKind> getResolveKinds(ClassHint hint) {
    EnumSet<ClassHint.ResolveKind> set = EnumSet.noneOf(ClassHint.ResolveKind.class);
    for (ClassHint.ResolveKind kind : ClassHint.ResolveKind.values()) {
      if (hint.shouldProcess(kind)) {
        set.add(kind);
      }
    }
    return set;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DeclarationCacheKey)) {
      return false;
    }

    DeclarationCacheKey key = (DeclarationCacheKey)o;

    if (nonCode != key.nonCode) {
      return false;
    }
    if (!kinds.equals(key.kinds)) {
      return false;
    }
    if (name != null ? !name.equals(key.name) : key.name != null) {
      return false;
    }

    if (place != key.place) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + kinds.hashCode();
    result = 31 * result + (nonCode ? 1 : 0);
    result = 31* result + place.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "DeclarationCacheKey{" +
           "name='" + name + '\'' +
           ", kinds=" + kinds +
           ", nonCode=" + nonCode +
           ", place=" + place.toString() +
           '}';
  }

  private List<DeclarationHolder> collectDeclarations(final PsiElement place) {
    final ArrayList<DeclarationHolder> result = new ArrayList<DeclarationHolder>();
    PsiTreeUtil.treeWalkUp(place, null, new PairProcessor<PsiElement, PsiElement>() {
      @Override
      public boolean process(PsiElement scope, PsiElement lastParent) {
        result.add(collectScopeDeclarations(scope, lastParent));
        if (nonCode && scope instanceof GrClosableBlock) return false; //closures tree walk up themselves if non code declarataions are acepted
        return true;
      }
    });
    return result;
  }

  private DeclarationHolder collectScopeDeclarations(PsiElement scope, PsiElement lastParent) {
    MyCollectProcessor plainCollector = new MyCollectProcessor(scope);
    MyCollectProcessor nonCodeCollector = new MyCollectProcessor(scope);
    ResolveUtil.doProcessDeclarations(place, lastParent, scope, plainCollector, nonCode ? nonCodeCollector : null, ResolveState.initial());
    return new DeclarationHolder(scope, plainCollector.declarations, nonCodeCollector.declarations);
  }

  private List<DeclarationHolder> getAllDeclarations(PsiElement place) {
    ConcurrentMap<DeclarationCacheKey, List<DeclarationHolder>> cache =
      CachedValuesManager.getManager(place.getProject()).getCachedValue(place, VALUE_PROVIDER);
    List<DeclarationHolder> declarations = cache.get(this);
    if (declarations == null) {
      declarations = collectDeclarations(place);
      cache.putIfAbsent(this, declarations);
    }
    return declarations;
  }

  boolean processCachedDeclarations(PsiElement place, PsiScopeProcessor processor) {
    for (DeclarationHolder holder : getAllDeclarations(place)) {
      if (!holder.processCachedDeclarations(processor)) {
        return false;
      }
    }
    return true;
  }

  private static class DeclarationHolder {
    final PsiElement scope;
    final List<Pair<PsiElement, ResolveState>> plainDeclarations;
    final List<Pair<PsiElement, ResolveState>> nonCodeDeclarations;

    private DeclarationHolder(PsiElement scope,
                              List<Pair<PsiElement, ResolveState>> plainDeclarations,
                              List<Pair<PsiElement, ResolveState>> nonCodeDeclarations) {
      this.scope = scope;
      this.plainDeclarations = plainDeclarations;
      this.nonCodeDeclarations = nonCodeDeclarations;
    }

    boolean processCachedDeclarations(PsiScopeProcessor processor) {
      PsiScopeProcessor realProcessor = ResolveUtil.substituteProcessor(processor, scope);
      for (Pair<PsiElement, ResolveState> pair : plainDeclarations) {
        if (!realProcessor.execute(pair.first, pair.second)) {
          return false;
        }
      }
      for (Pair<PsiElement, ResolveState> pair : nonCodeDeclarations) {
        if (!processor.execute(pair.first, pair.second)) {
          return false;
        }
      }

      ResolveUtil.issueLevelChangeEvents(processor, scope);
      return true;
    }

    @Override
    public String toString() {
      return "[scope=" + scope.toString() + ", plain=" + plainDeclarations.size() + ", nonCode=" + nonCodeDeclarations.size();
    }
  }

  private class MyCollectProcessor extends ResolverProcessorImpl
  {
    final List<Pair<PsiElement, ResolveState>> declarations = ContainerUtil.newArrayList();

    public MyCollectProcessor(PsiElement scope) {
      super(DeclarationCacheKey.this.name, DeclarationCacheKey.this.kinds, scope, PsiType.EMPTY_ARRAY);
    }

    @Override
    public boolean execute(@Nonnull PsiElement element, ResolveState state) {
      declarations.add(Pair.create(element, state));
      return true;
    }
  }
}

