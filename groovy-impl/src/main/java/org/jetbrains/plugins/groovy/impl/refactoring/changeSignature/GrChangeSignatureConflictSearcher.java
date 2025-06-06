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
package org.jetbrains.plugins.groovy.impl.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.java.impl.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
class GrChangeSignatureConflictSearcher {
    private static final Logger LOG = Logger.getInstance(GrChangeSignatureConflictSearcher.class);
    private final JavaChangeInfo myChangeInfo;

    GrChangeSignatureConflictSearcher(JavaChangeInfo changeInfo) {
        myChangeInfo = changeInfo;
    }

    public MultiMap<PsiElement, String> findConflicts(SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
        addMethodConflicts(conflictDescriptions);
        UsageInfo[] usagesIn = refUsages.get();
        RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
        Set<UsageInfo> usagesSet = new HashSet<>(Arrays.asList(usagesIn));
        RenameUtil.removeConflictUsages(usagesSet);
        if (myChangeInfo.isVisibilityChanged()) {
            try {
                addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }

        return conflictDescriptions;
    }

    private boolean needToChangeCalls() {
        return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
    }

    private void addInaccessibilityDescriptions(Set<UsageInfo> usages, MultiMap<PsiElement, String> conflictDescriptions)
        throws IncorrectOperationException {
        PsiMethod method = myChangeInfo.getMethod();
        PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
        VisibilityUtil.setVisibility(modifierList, myChangeInfo.getNewVisibility());

        for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext(); ) {
            UsageInfo usageInfo = iterator.next();
            if (usageInfo.getElement() instanceof GrReferenceExpression refExpr) {
                PsiClass accessObjectClass = null;
                GrExpression qualifier = refExpr.getQualifierExpression();
                if (qualifier != null) {
                    accessObjectClass = getAccessObjectClass(qualifier);
                }

                PsiResolveHelper helper = JavaPsiFacade.getInstance(refExpr.getProject()).getResolveHelper();
                if (!helper.isAccessible(method, modifierList, refExpr, accessObjectClass, null)) {
                    LocalizeValue message = RefactoringLocalize.zeroWith1VisibilityIsNotAccessibleFrom2(
                        RefactoringUIUtil.getDescription(method, true),
                        myChangeInfo.getNewVisibility(),
                        RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(refExpr), true)
                    );
                    conflictDescriptions.putValue(method, message.get());
                    if (!needToChangeCalls()) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    @Nullable
    private static PsiClass getAccessObjectClass(GrExpression expression) {
        if (expression instanceof GrConstructorInvocation) {
            return null;
        }
        PsiType type = expression.getType();
        if (type instanceof PsiClassType classType) {
            return classType.resolveGenerics().getElement();
        }
        if (type == null && expression instanceof PsiReferenceExpression refExpr) {
            JavaResolveResult resolveResult = refExpr.advancedResolve(false);
            if (resolveResult.getElement() instanceof PsiClass psiClass) {
                return psiClass;
            }
        }
        return null;
    }


    private void addMethodConflicts(MultiMap<PsiElement, String> conflicts) {
        try {
            GrMethod prototype;
            if (!(myChangeInfo.getMethod() instanceof GrMethod method)) {
                return;
            }

            PsiManager manager = method.getManager();
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());
            CanonicalTypes.Type returnType = myChangeInfo.getNewReturnType();
            String newMethodName = myChangeInfo.getNewName();
            if (method.isConstructor()) {
                prototype =
                    factory.createConstructorFromText("foo", ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}", method);
            }
            else {
                prototype = factory.createMethodFromText(
                    "",
                    "foo",
                    returnType != null ? returnType.getTypeText() : null,
                    ArrayUtil.EMPTY_STRING_ARRAY,
                    method
                );
            }
            prototype.setName(newMethodName);

            JavaParameterInfo[] parameters = myChangeInfo.getNewParameters();

            for (JavaParameterInfo info : parameters) {
                GrParameter param;
                if (info instanceof GrParameterInfo paramInfo) {
                    param = factory.createParameter(info.getName(), info.getTypeText(), paramInfo.getDefaultInitializer(), method);
                }
                else {
                    param = factory.createParameter(info.getName(), info.getTypeText(), method);
                }
                prototype.getParameterList().add(param);
            }

            ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflicts);
            GrMethodConflictUtil.checkMethodConflicts(method.getContainingClass(), prototype, method, conflicts, true);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }
}
