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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.Application;
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
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author ven
 */
@ExtensionImpl
public class GroovyDirectInheritorsSearcher implements DirectClassInheritorsSearchExecutor {
    public GroovyDirectInheritorsSearcher() {
    }

    @Nonnull
    @RequiredReadAction
    private static PsiClass[] getDeriverCandidates(PsiClass clazz, GlobalSearchScope scope) {
        String name = clazz.getName();
        if (name == null) {
            return GrTypeDefinition.EMPTY_ARRAY;
        }
        ArrayList<PsiClass> inheritors = new ArrayList<>();
        for (GrReferenceList list : StubIndex.getInstance()
            .safeGet(GrDirectInheritorsIndex.KEY, name, clazz.getProject(), scope, GrReferenceList.class)) {
            PsiElement parent = list.getParent();
            if (parent instanceof GrTypeDefinition typeDef) {
                inheritors.add(typeDef);
            }
        }
        Collection<GrAnonymousClassDefinition> classes =
            StubIndex.getInstance().get(GrAnonymousClassIndex.KEY, name, clazz.getProject(), scope);
        for (GrAnonymousClassDefinition aClass : classes) {
            inheritors.add(aClass);
        }
        return inheritors.toArray(new PsiClass[inheritors.size()]);
    }

    @Override
    public boolean execute(
        @Nonnull DirectClassInheritorsSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiClass> consumer
    ) {
        if (queryParameters.getScope() instanceof GlobalSearchScope globalSearchScope) {
            PsiClass clazz = queryParameters.getClassToProcess();
            PsiClass[] candidates = Application.get().runReadAction(
                (Supplier<PsiClass[]>)() -> clazz.isValid() ? getDeriverCandidates(clazz, globalSearchScope) : PsiClass.EMPTY_ARRAY
            );
            for (PsiClass candidate : candidates) {
                boolean isInheritor = AccessRule.read(() -> candidate.isValid() && candidate.isInheritor(clazz, false));

                if (isInheritor && !consumer.test(candidate)) {
                    return false;
                }
            }

            return true;
        }

        return true;
    }
}
