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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Max Medvedev
 */
public class GroovyInArgumentCheckInspection extends BaseInspection {
    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new MyVisitor();
    }

    @Override
    protected String buildErrorString(Object... args) {
        PsiType lType = (PsiType) args[0];
        PsiType rType = (PsiType) args[1];
        return GroovyInspectionLocalize.rtypeCannotContainLtype(lType.getPresentableText(), rType.getPresentableText()).get();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return BaseInspection.PROBABLE_BUGS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Incompatible 'in' argument types");
    }

    private static class MyVisitor extends BaseInspectionVisitor {
        @Override
        public void visitBinaryExpression(GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);

            if (expression.getOperationTokenType() != GroovyTokenTypes.kIN) {
                return;
            }

            GrExpression lOperand = expression.getLeftOperand();
            GrExpression rOperand = expression.getRightOperand();
            if (rOperand == null) {
                return;
            }

            PsiType lType = lOperand.getType();
            PsiType rType = rOperand.getType();
            if (lType == null || rType == null) {
                return;
            }

            PsiType component;

            if (rType instanceof PsiArrayType arrayType) {
                component = arrayType.getComponentType();
            }
            else if (InheritanceUtil.isInheritor(rType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                component = PsiUtil.substituteTypeParameter(rType, CommonClassNames.JAVA_UTIL_COLLECTION, 0, false);
            }
            else {
                checkSimpleClasses(lType, rType, expression);
                return;
            }

            if (component == null) {
                return;
            }

            if (TypesUtil.isAssignable(lType, component, expression)) {
                return;
            }

            registerError(expression, lType, rType);
        }

        private void checkSimpleClasses(PsiType lType, PsiType rType, GrBinaryExpression expression) {
            if (!(rType instanceof PsiClassType rClassType
                && lType instanceof PsiClassType lClassType)) {
                return;
            }

            PsiClass lClass = lClassType.resolve();
            PsiClass rClass = rClassType.resolve();

            if (lClass == null || rClass == null) {
                return;
            }

            if (expression.getManager().areElementsEquivalent(lClass, rClass)) {
                return;
            }

            if (lClass.isInterface() || rClass.isInterface()) {
                return;
            }

            if (lClass.isInheritor(rClass, true) || rClass.isInheritor(lClass, true)) {
                return;
            }

            registerError(expression, lType, rType);
        }
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
