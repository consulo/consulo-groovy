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
package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.indexing.impl.search.AnnotatedElementsSearcher;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearch;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearchExecutor;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.stub.StubIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author ven
 */
@ExtensionImpl
public class AnnotatedMembersSearcher implements AnnotatedElementsSearchExecutor {
    @Nonnull
    @RequiredReadAction
    private static List<PsiModifierListOwner> getAnnotatedMemberCandidates(PsiClass clazz, GlobalSearchScope scope) {
        String name = clazz.getName();
        if (name == null) {
            return Collections.emptyList();
        }
        Collection<PsiElement> members =
            AccessRule.read((() -> StubIndex.getInstance().get(GrAnnotatedMemberIndex.KEY, name, clazz.getProject(), scope)));
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<PsiModifierListOwner> result = new ArrayList<>();
        for (PsiElement element : members) {
            if (element instanceof GroovyFile groovyFile) {
                element = groovyFile.getPackageDefinition();
            }
            if (element instanceof PsiModifierListOwner modifierListOwner) {
                result.add(modifierListOwner);
            }
        }
        return result;
    }

    @Override
    @RequiredReadAction
    public boolean execute(
        @Nonnull AnnotatedElementsSearch.Parameters p,
        @Nonnull Predicate<? super PsiModifierListOwner> consumer
    ) {
        PsiClass annClass = p.getAnnotationClass();
        assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

        String annotationFQN = AccessRule.read(annClass::getName);

        assert annotationFQN != null;

        SearchScope scope = p.getScope();

        List<PsiModifierListOwner> candidates;
        if (scope instanceof GlobalSearchScope globalSearchScope) {
            candidates = getAnnotatedMemberCandidates(annClass, globalSearchScope);
        }
        else {
            candidates = new ArrayList<>();
            for (PsiElement element : ((LocalSearchScope)scope).getScope()) {
                if (element instanceof GroovyPsiElement groovyPsiElement) {
                    groovyPsiElement.accept(new GroovyRecursiveElementVisitor() {
                        @Override
                        public void visitMethod(GrMethod method) {
                            candidates.add(method);
                        }

                        @Override
                        public void visitField(GrField field) {
                            candidates.add(field);
                        }
                    });
                }
            }
        }

        for (PsiModifierListOwner candidate : candidates) {
            if (!AnnotatedElementsSearcher.isInstanceof(candidate, p.getTypes())) {
                continue;
            }

            boolean accepted = AccessRule.read(() -> {
                PsiModifierList list = candidate.getModifierList();
                if (list != null) {
                    for (PsiAnnotation annotation : list.getAnnotations()) {
                        if (annotationFQN.equals(annotation.getQualifiedName()) && !consumer.test(candidate)) {
                            return false;
                        }
                    }
                }

                return true;
            });

            if (!accepted) {
                return false;
            }
        }

        return true;
    }
}
