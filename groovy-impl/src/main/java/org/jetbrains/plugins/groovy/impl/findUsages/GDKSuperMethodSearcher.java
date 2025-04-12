/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearchExecutor;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.ResolveState;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GDKSuperMethodSearcher implements SuperMethodsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull SuperMethodsSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super MethodSignatureBackedByPsiMethod> consumer
    ) {
        PsiMethod method = queryParameters.getMethod();
        if (!(method instanceof GrMethod) || method.isStatic()) {
            return true;
        }

        PsiClass psiClass = method.getContainingClass();
        if (psiClass == null) {
            return true;
        }

        HierarchicalMethodSignature hierarchicalSignature = method.getHierarchicalMethodSignature();
        if (hierarchicalSignature.getSuperSignatures().size() != 0) {
            return true;
        }

        Project project = method.getProject();

        String name = method.getName();
        MethodResolverProcessor processor = new MethodResolverProcessor(
            name,
            method,
            false,
            null,
            null,
            PsiType.EMPTY_ARRAY
        );
        ResolveUtil.processNonCodeMembers(
            JavaPsiFacade.getElementFactory(project).createType(psiClass),
            processor,
            method,
            ResolveState.initial()
        );

        GroovyResolveResult[] candidates = processor.getCandidates();

        PsiManager psiManager = PsiManager.getInstance(project);

        MethodSignature signature = method.getHierarchicalMethodSignature();
        List<PsiMethod> goodSupers = new ArrayList<>();

        for (GroovyResolveResult candidate : candidates) {
            if (candidate.getElement() instanceof PsiMethod m && !isTheSameMethod(method, psiManager, m)
                && PsiImplUtil.isExtendsSignature(m.getHierarchicalMethodSignature(), signature)) {
                goodSupers.add(m);
            }
        }

        if (goodSupers.size() == 0) {
            return true;
        }

        List<PsiMethod> result = new ArrayList<>(goodSupers.size());
        result.add(goodSupers.get(0));

        Comparator<PsiMethod> comparator = (o1, o2) -> { //compare by first parameter type
            PsiType type1 = getRealType(o1);
            PsiType type2 = getRealType(o2);
            if (TypesUtil.isAssignableByMethodCallConversion(type1, type2, o1)) {
                return -1;
            }
            else if (TypesUtil.isAssignableByMethodCallConversion(type2, type1, o1)) {
                return 1;
            }
            return 0;
        };

        Outer:
        for (PsiMethod current : goodSupers) {
            for (Iterator<PsiMethod> i = result.iterator(); i.hasNext(); ) {
                PsiMethod m = i.next();
                int res = comparator.compare(m, current);
                if (res > 0) {
                    continue Outer;
                }
                else if (res < 0) {
                    i.remove();
                }
            }
            result.add(current);
        }
        for (PsiMethod psiMethod : result) {
            if (!consumer.test(getRealMethod(psiMethod).getHierarchicalMethodSignature())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTheSameMethod(PsiMethod method, PsiManager psiManager, PsiMethod m) {
        return psiManager.areElementsEquivalent(m, method) || psiManager.areElementsEquivalent(m.getNavigationElement(), method);
    }

    private static PsiMethod getRealMethod(PsiMethod method) {
        return method.getNavigationElement() instanceof PsiMethod navMethod && navMethod.getParameterList().getParametersCount() > 0
            ? navMethod
            : method;
    }

    @Nullable
    private static PsiType getRealType(PsiMethod method) {
        if (method.getNavigationElement() instanceof PsiMethod navMethod) {
            PsiParameter[] parameters = navMethod.getParameterList().getParameters();
            if (parameters.length != 0) {
                return TypeConversionUtil.erasure(parameters[0].getType());
            }
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        return JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass);
    }
}
