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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.SingleIntegerFieldOptionsPanel;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import javax.swing.*;
import java.util.Set;

public class GroovyOverlyComplexArithmeticExpressionInspection extends BaseInspection {
    private static final int TERM_LIMIT = 3;

    /**
     * @noinspection PublicField, WeakerAccess
     */
    public int m_limit = TERM_LIMIT;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Overly complex arithmetic expression");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    private int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Maximum number of terms:", this, "m_limit");
    }

    @Override
    protected String buildErrorString(Object... args) {
        return "Overly complex arithmetic expression #loc";
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static final Set<IElementType> ARITHMETIC_TOKENS = Set.of(
        GroovyTokenTypes.mPLUS,
        GroovyTokenTypes.mMINUS,
        GroovyTokenTypes.mSTAR,
        GroovyTokenTypes.mDIV,
        GroovyTokenTypes.mMOD
    );

    private class Visitor extends BaseInspectionVisitor {
        @Override
        public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitUnaryExpression(@Nonnull GrUnaryExpression expression) {
            super.visitUnaryExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(GrExpression expression) {
            if (isParentArithmetic(expression)) {
                return;
            }
            if (!isArithmetic(expression)) {
                return;
            }
            if (containsStringConcatenation(expression)) {
                return;
            }
            int numTerms = countTerms(expression);
            if (numTerms <= getLimit()) {
                return;
            }
            registerError(expression);
        }

        private int countTerms(GrExpression expression) {
            if (expression == null) {
                return 0;
            }
            if (!isArithmetic(expression)) {
                return 1;
            }
            if (expression instanceof GrBinaryExpression binaryExpression) {
                GrExpression lhs = binaryExpression.getLeftOperand();
                GrExpression rhs = binaryExpression.getRightOperand();
                return countTerms(lhs) + countTerms(rhs);
            }
            else if (expression instanceof GrUnaryExpression unaryExpression) {
                GrExpression operand = unaryExpression.getOperand();
                return countTerms(operand);
            }
            else if (expression instanceof GrParenthesizedExpression parenthesizedExpression) {
                GrExpression contents = parenthesizedExpression.getOperand();
                return countTerms(contents);
            }
            return 1;
        }

        private boolean isParentArithmetic(GrExpression expression) {
            return expression.getParent() instanceof GrExpression parent && isArithmetic(parent);
        }

        private boolean isArithmetic(GrExpression expression) {
            if (expression instanceof GrBinaryExpression binaryExpression) {
                return ARITHMETIC_TOKENS.contains(binaryExpression.getOperationTokenType());
            }
            else if (expression instanceof GrUnaryExpression unaryExpr) {
                return ARITHMETIC_TOKENS.contains(unaryExpr.getOperationTokenType());
            }
            else if (expression instanceof GrParenthesizedExpression parenthesized) {
                return isArithmetic(parenthesized.getOperand());
            }
            return false;
        }

        private boolean containsStringConcatenation(GrExpression expression) {
            if (isString(expression)) {
                return true;
            }
            if (expression instanceof GrBinaryExpression binaryExpr) {
                GrExpression lhs = binaryExpr.getLeftOperand();

                if (containsStringConcatenation(lhs)) {
                    return true;
                }
                GrExpression rhs = binaryExpr.getRightOperand();
                return containsStringConcatenation(rhs);
            }
            else if (expression instanceof GrUnaryExpression unaryExpr) {
                return containsStringConcatenation(unaryExpr.getOperand());
            }
            else if (expression instanceof GrParenthesizedExpression parenthesized) {
                return containsStringConcatenation(parenthesized.getOperand());
            }
            return false;
        }

        private boolean isString(GrExpression expression) {
            if (expression == null) {
                return false;
            }
            PsiType type = expression.getType();
            return type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText());
        }
    }
}