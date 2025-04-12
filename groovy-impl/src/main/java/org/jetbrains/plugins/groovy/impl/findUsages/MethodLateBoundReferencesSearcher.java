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

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearchExecutor;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.RequestResultProcessor;
import consulo.language.psi.search.SearchRequestCollector;
import consulo.language.psi.search.UsageSearchContext;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;

import java.util.function.Predicate;

/**
 * @author ven
 */
@ExtensionImpl
public class MethodLateBoundReferencesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>
    implements MethodReferencesSearchExecutor {
    public MethodLateBoundReferencesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(
        @Nonnull MethodReferencesSearch.SearchParameters queryParameters,
        @Nonnull Processor<? super PsiReference> consumer
    ) {
        final PsiMethod method = queryParameters.getMethod();
        SearchScope searchScope = GroovyScopeUtil.restrictScopeToGroovyFiles(queryParameters.getScope()).intersectWith(getUseScope(method));

        orderSearching(searchScope, method.getName(), queryParameters.getOptimizer(), method.getParameterList().getParametersCount());

        final String propName = PropertyUtil.getPropertyName(method);
        if (propName != null) {
            orderSearching(searchScope, propName, queryParameters.getOptimizer(), -1);
        }
    }

    private static SearchScope getUseScope(final PsiMethod method) {
        final SearchScope scope = method.getUseScope();
        final PsiFile file = method.getContainingFile();
        if (file != null && scope instanceof GlobalSearchScope) {
            final VirtualFile vfile = file.getOriginalFile().getVirtualFile();
            final Project project = method.getProject();
            if (vfile != null && ProjectRootManager.getInstance(project).getFileIndex().isInSource(vfile)) {
                return new GrSourceFilterScope((GlobalSearchScope)scope);
            }
        }
        return scope;
    }


    private static void orderSearching(
        SearchScope searchScope,
        final String name,
        @Nonnull SearchRequestCollector collector,
        final int paramCount
    ) {
        if (StringUtil.isEmpty(name)) {
            return;
        }
        collector.searchWord(name, searchScope, UsageSearchContext.IN_CODE, true, new RequestResultProcessor("groovy.lateBound") {
            @Override
            public boolean processTextOccurrence(
                @Nonnull PsiElement element,
                int offsetInElement,
                @Nonnull Predicate<? super PsiReference> consumer
            ) {
                if (!(element instanceof GrReferenceExpression)) {
                    return true;
                }

                final GrReferenceExpression ref = (GrReferenceExpression)element;
                if (!name.equals(ref.getReferenceName()) || PsiUtil.isLValue(ref) || ref.resolve() != null) {
                    return true;
                }

                PsiElement parent = ref.getParent();
                if (parent instanceof GrMethodCall) {
                    if (!argumentsMatch((GrMethodCall)parent, paramCount)) {
                        return true;
                    }
                }
                else if (ResolveUtil.isKeyOfMap(ref)) {
                    return true;
                }

                return consumer.test((PsiReference)element);
            }
        });
    }

    private static boolean argumentsMatch(GrMethodCall call, int paramCount) {
        int argCount = call.getExpressionArguments().length;
        if (PsiImplUtil.hasNamedArguments(call.getArgumentList())) {
            argCount++;
        }
        return argCount == paramCount;
    }
}