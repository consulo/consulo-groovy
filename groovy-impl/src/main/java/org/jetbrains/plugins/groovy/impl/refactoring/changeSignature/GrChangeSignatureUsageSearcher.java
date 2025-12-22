/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.changeSignature.*;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.java.impl.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.changeSignature.ParameterInfo;
import consulo.language.editor.refactoring.changeSignature.PsiCallReference;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
class GrChangeSignatureUsageSearcher {
    private final JavaChangeInfo myChangeInfo;
    private static final Logger LOG = Logger.getInstance(GrChangeSignatureUsageSearcher.class);

    GrChangeSignatureUsageSearcher(JavaChangeInfo changeInfo) {
        this.myChangeInfo = changeInfo;
    }

    @RequiredReadAction
    public UsageInfo[] findUsages() {
        ArrayList<UsageInfo> result = new ArrayList<>();
        if (myChangeInfo.getMethod() instanceof PsiMethod method) {
            findSimpleUsages(method, result);

            UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
            return UsageViewUtil.removeDuplicatedUsages(usageInfos);
        }
        return UsageInfo.EMPTY_ARRAY;
    }

    @RequiredReadAction
    private void findSimpleUsages(PsiMethod method, List<UsageInfo> result) {
        PsiMethod[] overridingMethods = findSimpleUsagesWithoutParameters(method, result, true, true, true);
        //findUsagesInCallers(result); todo

        //Parameter name changes are not propagated
        findParametersUsage(method, result, overridingMethods);
    }

    /* todo
    private void findUsagesInCallers(final ArrayList<UsageInfo> usages) {
        if (myChangeInfo instanceof JavaChangeInfoImpl) {
            JavaChangeInfoImpl changeInfo = (JavaChangeInfoImpl)myChangeInfo;

            for (PsiMethod caller : changeInfo.propagateParametersMethods) {
                usages.add(new CallerUsageInfo(caller, true, changeInfo.propagateExceptionsMethods.contains(caller)));
            }
            for (PsiMethod caller : changeInfo.propagateExceptionsMethods) {
                usages.add(new CallerUsageInfo(caller, changeInfo.propagateParametersMethods.contains(caller), true));
            }
            Set<PsiMethod> merged = new HashSet<PsiMethod>();
            merged.addAll(changeInfo.propagateParametersMethods);
            merged.addAll(changeInfo.propagateExceptionsMethods);
            for (final PsiMethod method : merged) {
                findSimpleUsagesWithoutParameters(
                    method,
                    usages,
                    changeInfo.propagateParametersMethods.contains(method),
                    changeInfo.propagateExceptionsMethods.contains(method),
                    false
                );
            }
        }
    }
    */

    @RequiredReadAction
    private void detectLocalsCollisionsInMethod(GrMethod method, List<UsageInfo> result, boolean isOriginal) {
        if (!GroovyLanguage.INSTANCE.equals(method.getLanguage())) {
            return;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        Set<PsiParameter> deletedOrRenamedParameters = new HashSet<>();
        if (isOriginal) {
            ContainerUtil.addAll(deletedOrRenamedParameters, parameters);
            for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
                if (parameterInfo.getOldIndex() >= 0) {
                    PsiParameter parameter = parameters[parameterInfo.getOldIndex()];
                    if (parameterInfo.getName().equals(parameter.getName())) {
                        deletedOrRenamedParameters.remove(parameter);
                    }
                }
            }
        }
        GrOpenBlock block = method.getBlock();
        for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
            int oldParameterIndex = parameterInfo.getOldIndex();
            String newName = parameterInfo.getName();
            if (oldParameterIndex >= 0) {
                if (isOriginal) {   //Name changes take place only in primary method
                    PsiParameter parameter = parameters[oldParameterIndex];
                    if (!newName.equals(parameter.getName())) {
                        GrUnresolvableLocalCollisionDetector.CollidingVariableVisitor collidingVariableVisitor =
                            collidingVariable -> {
                                if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                                    result.add(new RenamedParameterCollidesWithLocalUsageInfo(parameter, collidingVariable, method));
                                }
                            };
                        if (block != null) {
                            GrUnresolvableLocalCollisionDetector.visitLocalsCollisions(parameter, newName, block, collidingVariableVisitor);
                        }
                    }
                }
            }
            else {
                GrUnresolvableLocalCollisionDetector.CollidingVariableVisitor variableVisitor = collidingVariable -> {
                    if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                        result.add(new NewParameterCollidesWithLocalUsageInfo(collidingVariable, collidingVariable, method));
                    }
                };
                if (block != null) {
                    GrUnresolvableLocalCollisionDetector.visitLocalsCollisions(method, newName, block, variableVisitor);
                }
            }
        }
    }

    @RequiredReadAction
    private void findParametersUsage(PsiMethod method, List<UsageInfo> result, PsiMethod[] overriders) {
        if (!GroovyLanguage.INSTANCE.equals(method.getLanguage())) {
            return;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (ParameterInfo info : myChangeInfo.getNewParameters()) {
            if (info.getOldIndex() >= 0) {
                PsiParameter parameter = parameters[info.getOldIndex()];
                if (!info.getName().equals(parameter.getName())) {
                    addParameterUsages(parameter, result, info);

                    for (PsiMethod overrider : overriders) {
                        if (!GroovyLanguage.INSTANCE.equals(overrider.getLanguage())) {
                            continue;
                        }
                        PsiParameter parameter1 = overrider.getParameterList().getParameters()[info.getOldIndex()];
                        if (parameter.getName().equals(parameter1.getName())) {
                            addParameterUsages(parameter1, result, info);
                        }
                    }
                }
            }
        }
    }

    @RequiredReadAction
    private PsiMethod[] findSimpleUsagesWithoutParameters(
        PsiMethod method,
        List<UsageInfo> result,
        boolean isToModifyArgs,
        boolean isToThrowExceptions,
        boolean isOriginal
    ) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(method.getProject());
        PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY);

        for (PsiMethod overridingMethod : overridingMethods) {
            if (GroovyLanguage.INSTANCE.equals(overridingMethod.getLanguage())) {
                result.add(new OverriderUsageInfo(overridingMethod, method, isOriginal, isToModifyArgs, isToThrowExceptions));
            }
        }

        boolean needToChangeCalls = !myChangeInfo.isGenerateDelegate()
            && (myChangeInfo.isNameChanged() ||
            myChangeInfo.isParameterSetOrOrderChanged() ||
            myChangeInfo.isExceptionSetOrOrderChanged() ||
            myChangeInfo.isVisibilityChanged()/*for checking inaccessible*/);
        if (needToChangeCalls) {
            PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
            for (PsiReference ref : refs) {
                PsiElement element = ref.getElement();

                if (!GroovyLanguage.INSTANCE.equals(element.getLanguage())) {
                    continue;
                }

                boolean isToCatchExceptions = isToThrowExceptions && needToCatchExceptions(RefactoringUtil.getEnclosingMethod(element));
                if (PsiUtil.isMethodUsage(element)) {
                    result.add(new GrMethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions, method));
                }
                else if (element instanceof GrDocTagValueToken) {
                    result.add(new UsageInfo(ref.getElement()));
                }
                else if (element instanceof GrMethod grMethod && grMethod.isConstructor()) {
                    DefaultConstructorImplicitUsageInfo implicitUsageInfo =
                        new DefaultConstructorImplicitUsageInfo(grMethod, grMethod.getContainingClass(), method);
                    result.add(implicitUsageInfo);
                }
                else if (element instanceof PsiClass psiClass) {
                    LOG.assertTrue(method.isConstructor());
                    if (psiClass instanceof GrAnonymousClassDefinition) {
                        result.add(new GrMethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions, method));
                        continue;
                    }
                    /*
                    if (!(myChangeInfo instanceof JavaChangeInfoImpl changeInfoImpl)) {
                        continue; todo propagate methods
                    }
                    if (shouldPropagateToNonPhysicalMethod(method, result, psiClass, changeInfoImpl.propagateParametersMethods)) {
                        continue;
                    }
                    if (shouldPropagateToNonPhysicalMethod(method, result, psiClass, changeInfoImpl.propagateExceptionsMethods)) {
                        continue;
                    }*/
                    result.add(new NoConstructorClassUsageInfo(psiClass));
                }
                else if (ref instanceof PsiCallReference) {
                    result.add(new CallReferenceUsageInfo((PsiCallReference) ref));
                }
                else {
                    result.add(new MoveRenameUsageInfo(element, ref, method));
                }
            }
        }
        else if (myChangeInfo.isParameterTypesChanged()) {
            PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
            for (PsiReference reference : refs) {
                if (reference.getElement() instanceof GrDocTagValueToken) {
                    result.add(new UsageInfo(reference));
                }
            }
        }

        // Conflicts
        if (method instanceof GrMethod grMethod) {
            detectLocalsCollisionsInMethod(grMethod, result, isOriginal);
        }
        for (PsiMethod overridingMethod : overridingMethods) {
            if (overridingMethod instanceof GrMethod grOverridingMethod) {
                detectLocalsCollisionsInMethod(grOverridingMethod, result, isOriginal);
            }
        }

        return overridingMethods;
    }

    @RequiredReadAction
    private static void addParameterUsages(PsiParameter parameter, List<UsageInfo> results, ParameterInfo info) {
        PsiManager manager = parameter.getManager();
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
        for (PsiReference psiReference : ReferencesSearch.search(parameter, projectScope, false)) {
            PsiElement paramRef = psiReference.getElement();
            UsageInfo usageInfo = new ChangeSignatureParameterUsageInfo(paramRef, parameter.getName(), info.getName());
            results.add(usageInfo);
        }
        if (info.getName() != parameter.getName()) {
        }
    }

    private boolean needToCatchExceptions(PsiMethod caller) {
    /*if (myChangeInfo instanceof JavaChangeInfoImpl changeInfoImpl) { //todo propagate methods
        return myChangeInfo.isExceptionSetOrOrderChanged() && !changeInfoImpl.propagateExceptionsMethods.contains(caller);
    }
    else {*/
        return myChangeInfo.isExceptionSetOrOrderChanged();
    }

    private static class RenamedParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
        private final PsiElement myCollidingElement;
        private final PsiMethod myMethod;

        public RenamedParameterCollidesWithLocalUsageInfo(PsiParameter parameter, PsiElement collidingElement, PsiMethod method) {
            super(parameter, collidingElement);
            myCollidingElement = collidingElement;
            myMethod = method;
        }

        @Nonnull
        @Override
        public LocalizeValue getDescription() {
            return RefactoringLocalize.thereIsAlreadyA0InThe1ItWillConflictWithTheRenamedParameter(
                RefactoringUIUtil.getDescription(myCollidingElement, true),
                RefactoringUIUtil.getDescription(myMethod, true)
            );
        }
    }
}
