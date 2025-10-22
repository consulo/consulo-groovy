/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.access.RequiredReadAction;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Max Medvedev
 */
public class UnnecessaryQualifiedReferenceInspection extends BaseInspection {
    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            @RequiredReadAction
            public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
                super.visitCodeReferenceElement(refElement);

                if (canBeSimplified(refElement)) {
                    registerError(refElement);
                }
            }

            @Override
            @RequiredReadAction
            public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
                super.visitReferenceExpression(referenceExpression);

                if (canBeSimplified(referenceExpression) || isQualifiedStaticMethodWithUnnecessaryQualifier(referenceExpression)) {
                    registerError(referenceExpression);
                }
            }
        };
    }

    private static boolean isQualifiedStaticMethodWithUnnecessaryQualifier(GrReferenceExpression ref) {
        if (ref.getQualifier() == null || !(ref.resolve() instanceof PsiMember member) || !member.isStatic()) {
            return false;
        }

        PsiElement copyResolved;
        PsiElement parent = ref.getParent();
        if (parent instanceof GrMethodCall methodCall) {
            GrMethodCall copy = (GrMethodCall) methodCall.copy();
            GrReferenceExpression invoked = (GrReferenceExpression) copy.getInvokedExpression();
            assert invoked != null;

            invoked.setQualifier(null);

            copyResolved = ((GrReferenceExpression) copy.getInvokedExpression()).resolve();
        }
        else {
            GrReferenceExpression copy = (GrReferenceExpression) ref.copy();
            copy.setQualifier(null);
            copyResolved = copy.resolve();
        }
        return ref.getManager().areElementsEquivalent(copyResolved, member);
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.unnecessaryQualifiedReference();
    }

    @Override
    protected String buildErrorString(Object... args) {
        return GroovyInspectionLocalize.unnecessaryQualifiedReference().get();
    }

    @Override
    protected GroovyFix buildFix(@Nonnull PsiElement location) {
        return GroovyQuickFixFactory.getInstance().createReplaceWithImportFix();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @RequiredReadAction
    private static boolean canBeSimplified(PsiElement element) {
        if (PsiTreeUtil.getParentOfType(element, PsiComment.class) != null) {
            return false;
        }

        if (element instanceof GrCodeReferenceElement) {
            if (PsiTreeUtil.getParentOfType(element, GrImportStatement.class, GrPackageDefinition.class) != null) {
                return false;
            }
        }
        else if (element instanceof GrReferenceExpression refExpr) {
            if (!PsiImplUtil.seemsToBeQualifiedClassName(refExpr)) {
                return false;
            }
        }
        else {
            return false;
        }

        GrReferenceElement ref = (GrReferenceElement) element;
        if (ref.getQualifier() == null) {
            return false;
        }
        if (!(ref.getContainingFile() instanceof GroovyFileBase)) {
            return false;
        }

        if (!(ref.resolve() instanceof PsiClass psiClass)) {
            return false;
        }

        String name = psiClass.getName();
        if (name == null) {
            return false;
        }

        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
        GrReferenceExpression shortedRef = factory.createReferenceExpressionFromText(name, element);
        GroovyResolveResult resolveResult = shortedRef.advancedResolve();

        if (element.getManager().areElementsEquivalent(psiClass, resolveResult.getElement())) {
            return true;
        }

        PsiClass containingClass = psiClass.getContainingClass();
        if (containingClass != null
            && !GroovyCodeStyleSettingsFacade.getInstance(containingClass.getProject()).insertInnerClassImports()) {
            return false;
        }

        return resolveResult.getElement() == null || !resolveResult.isAccessible() || !resolveResult.isStaticsOK();
    }
}
