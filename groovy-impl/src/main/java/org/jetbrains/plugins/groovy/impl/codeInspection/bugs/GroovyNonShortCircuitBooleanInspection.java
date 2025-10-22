/*
 * Copyright 2007-2008 Dave Griffith
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

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyNonShortCircuitBooleanInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PROBABLE_BUGS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Non short-circuit boolean");
    }

    @Nullable
    @Override
    protected String buildErrorString(Object... args) {
        return "Non short-circuit boolean expression #loc";
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new NonShortCircuitBooleanFix();
    }

    private static class NonShortCircuitBooleanFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Replace with short-circuit expression");
        }

        @Override
        @RequiredReadAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrBinaryExpression expression = (GrBinaryExpression) descriptor.getPsiElement();
            GrExpression lhs = expression.getLeftOperand();
            GrExpression rhs = expression.getRightOperand();
            IElementType operationSign = expression.getOperationTokenType();
            assert rhs != null;
            String newExpression = lhs.getText() + getShortCircuitOperand(operationSign) + rhs.getText();
            replaceExpression(expression, newExpression);
        }

        private static String getShortCircuitOperand(IElementType tokenType) {
            if (tokenType.equals(GroovyTokenTypes.mBAND)) {
                return "&&";
            }
            else {
                return "||";
            }
        }
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        @Override
        public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            GrExpression rhs = expression.getRightOperand();
            if (rhs == null) {
                return;
            }
            IElementType sign = expression.getOperationTokenType();
            if (!GroovyTokenTypes.mBAND.equals(sign) &&
                !GroovyTokenTypes.mBOR.equals(sign)) {
                return;
            }
            if (!PsiType.BOOLEAN.equals(rhs.getType())) {
                return;
            }
            registerError(expression);
        }
    }
}