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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.CreateFieldFromConstructorLabelFix;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyConstructorNamedArgumentsInspection extends BaseInspection {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new MyVisitor();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PROBABLE_BUGS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Named arguments of constructor call");
    }

    @Override
    protected String buildErrorString(Object... args) {
        assert args.length == 1 && args[0] instanceof String;
        return (String) args[0];
    }

    private static class MyVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitNewExpression(GrNewExpression newExpression) {
            super.visitNewExpression(newExpression);

            GrCodeReferenceElement refElement = newExpression.getReferenceElement();
            if (refElement == null) {
                return;
            }

            GroovyResolveResult constructorResolveResult = newExpression.advancedResolve();
            PsiElement constructor = constructorResolveResult.getElement();
            if (constructor != null) {
                GrArgumentList argList = newExpression.getArgumentList();
                if (argList != null && argList.getExpressionArguments().length == 0
                    && !PsiUtil.isConstructorHasRequiredParameters((PsiMethod) constructor)) {
                    checkDefaultMapConstructor(argList, constructor);
                }
            }
            else {
                GroovyResolveResult[] results = newExpression.multiResolveGroovy(false);
                if (results.length == 0 && refElement.resolve() instanceof PsiClass psiClass) { //default constructor invocation
                    PsiType[] argTypes = PsiUtil.getArgumentTypes(refElement, true);
                    if (argTypes == null || argTypes.length == 0
                        || (argTypes.length == 1 && InheritanceUtil.isInheritor(argTypes[0], CommonClassNames.JAVA_UTIL_MAP))) {
                        checkDefaultMapConstructor(newExpression.getArgumentList(), psiClass);
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkDefaultMapConstructor(GrArgumentList argList, PsiElement element) {
            if (argList == null) {
                return;
            }

            GrNamedArgument[] args = argList.getNamedArguments();
            for (GrNamedArgument arg : args) {
                GrArgumentLabel label = arg.getLabel();
                if (label == null) {
                    continue;
                }
                if (label.getName() == null) {
                    PsiElement nameElement = label.getNameElement();
                    if (nameElement instanceof GrExpression expression) {
                        PsiType argType = expression.getType();
                        if (argType != null
                            && !TypesUtil.isAssignableByMethodCallConversion(
                            TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, arg),
                            argType,
                            arg
                        )) {
                            registerError(expression, GroovyLocalize.propertyNameExpected().get());
                        }
                    }
                    else if (!"*".equals(nameElement.getText())) {
                        registerError(nameElement, GroovyLocalize.propertyNameExpected().get());
                    }
                }
                else {
                    PsiElement resolved = label.resolve();
                    if (resolved == null) {

                        if (element instanceof PsiMember member && !(member instanceof PsiClass)) {
                            element = member.getContainingClass();
                        }

                        List<LocalQuickFix> fixes = new ArrayList<>(2);
                        if (element instanceof GrTypeDefinition typeDef) {
                            fixes.add(new CreateFieldFromConstructorLabelFix(typeDef, label.getNamedArgument()));
                        }
                        if (element instanceof PsiClass psiClass) {
                            fixes.add(new DynamicPropertyFix(label, psiClass));
                        }

                        problemsHolder.newProblem(GroovyLocalize.noSuchProperty(label.getName()))
                            .range((PsiElement) label)
                            .withFixes(fixes.toArray(new LocalQuickFix[fixes.size()]))
                            .create();
                    }
                }
            }
        }
    }
}
