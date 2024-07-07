/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.psi.impl;

import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearchExecutor;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StubIndex;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ven
 */
@ExtensionImpl
public class GroovyDirectInheritorsSearcher implements DirectClassInheritorsSearchExecutor {

  public GroovyDirectInheritorsSearcher() {
  }

  @Nonnull
  private static PsiClass[] getDeriverCandidates(PsiClass clazz, GlobalSearchScope scope) {
    final String name = clazz.getName();
    if (name == null) {
      return GrTypeDefinition.EMPTY_ARRAY;
    }
    final ArrayList<PsiClass> inheritors = new ArrayList<PsiClass>();
    for (GrReferenceList list : StubIndex.getInstance()
                                         .safeGet(GrDirectInheritorsIndex.KEY, name, clazz.getProject(), scope, GrReferenceList.class)) {
      final PsiElement parent = list.getParent();
      if (parent instanceof GrTypeDefinition) {
        inheritors.add(((GrTypeDefinition)parent));
      }
    }
    final Collection<GrAnonymousClassDefinition> classes =
      StubIndex.getInstance().get(GrAnonymousClassIndex.KEY, name, clazz.getProject(), scope);
    for (GrAnonymousClassDefinition aClass : classes) {
      inheritors.add(aClass);
    }
    return inheritors.toArray(new PsiClass[inheritors.size()]);
  }

  public boolean execute(@Nonnull DirectClassInheritorsSearch.SearchParameters queryParameters,
                         @Nonnull final Processor<? super PsiClass> consumer) {
    final PsiClass clazz = queryParameters.getClassToProcess();
    final SearchScope scope = queryParameters.getScope();
    if (scope instanceof GlobalSearchScope) {
      final PsiClass[] candidates = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
        public PsiClass[] compute() {
          if (!clazz.isValid()) {
            return PsiClass.EMPTY_ARRAY;
          }
          return getDeriverCandidates(clazz, (GlobalSearchScope)scope);
        }
      });
      for (final PsiClass candidate : candidates) {
        final boolean isInheritor = AccessRule.read(() -> candidate.isValid() && candidate.isInheritor(clazz, false));

        if (isInheritor) {
          if (!consumer.process(candidate)) {
            return false;
          }
        }
      }

      return true;
    }

    return true;
  }
}
