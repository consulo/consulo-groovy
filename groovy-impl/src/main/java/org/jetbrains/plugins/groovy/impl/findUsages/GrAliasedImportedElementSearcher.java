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
package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.*;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import jakarta.annotation.Nullable;

import java.util.function.Predicate;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GrAliasedImportedElementSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {
    public GrAliasedImportedElementSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@Nonnull ReferencesSearch.SearchParameters parameters, @Nonnull Processor<? super PsiReference> consumer) {
        final PsiElement target = parameters.getElementToSearch();
        if (!(target instanceof PsiMember) || !(target instanceof PsiNamedElement)) {
            return;
        }

        final String name = ((PsiNamedElement)target).getName();
        if (name == null || StringUtil.isEmptyOrSpaces(name)) {
            return;
        }

        final SearchScope onlyGroovy = GroovyScopeUtil.restrictScopeToGroovyFiles(parameters.getEffectiveSearchScope());

        final SearchRequestCollector collector = parameters.getOptimizer();
        final SearchSession session = collector.getSearchSession();
        if (target instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)target;
            if (GroovyPropertyUtils.isSimplePropertyAccessor(method)) {
                final PsiField field = GroovyPropertyUtils.findFieldForAccessor(method, true);
                if (field != null) {
                    final String propertyName = field.getName();
                    if (propertyName != null) {
                        final MyProcessor processor = new MyProcessor(method, GroovyPropertyUtils.getAccessorPrefix(method), session);
                        collector.searchWord(propertyName, onlyGroovy, UsageSearchContext.IN_CODE, true, processor);
                    }
                }
            }
        }

        collector.searchWord(name, onlyGroovy, UsageSearchContext.IN_CODE, true, new MyProcessor(target, null, session));

    }

    private static class MyProcessor extends RequestResultProcessor {
        private final PsiElement myTarget;
        private final String prefix;
        private final SearchSession mySession;

        MyProcessor(PsiElement target, @Nullable String prefix, SearchSession session) {
            super(target, prefix);
            myTarget = target;
            this.prefix = prefix;
            mySession = session;
        }

        @Override
        public boolean processTextOccurrence(
            @Nonnull final PsiElement element,
            int offsetInElement,
            @Nonnull Predicate<? super PsiReference> consumer
        ) {
            String alias = getAlias(element);
            if (alias == null) {
                return true;
            }

            final PsiReference reference = element.getReference();
            if (reference == null) {
                return true;
            }
            if (!reference.isReferenceTo(myTarget instanceof GrAccessorMethod ? ((GrAccessorMethod)myTarget).getProperty() : myTarget)) {
                return true;
            }

            final SearchRequestCollector collector = new SearchRequestCollector(mySession);
            final SearchScope fileScope = new LocalSearchScope(element.getContainingFile());
            collector.searchWord(alias, fileScope, UsageSearchContext.IN_CODE, true, myTarget);
            if (prefix != null) {
                collector.searchWord(prefix + GroovyPropertyUtils.capitalize(alias), fileScope, UsageSearchContext.IN_CODE, true, myTarget);
            }


            return PsiSearchHelper.getInstance(element.getProject()).processRequests(collector, consumer);
        }

        @Nullable
        private static String getAlias(final PsiElement element) {
            if (!(element.getParent() instanceof GrImportStatement)) {
                return null;
            }
            final GrImportStatement importStatement = (GrImportStatement)element.getParent();
            if (!importStatement.isAliasedImport()) {
                return null;
            }
            return importStatement.getImportedName();
        }
    }
}
