/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

public class GroovyPointlessArithmeticInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Pointless arithmetic expression");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PointlessArithmeticVisitor();
    }

    @Override
    @RequiredReadAction
    public String buildErrorString(Object... args) {
        return GroovyInspectionLocalize.pointlessArithmeticErrorMessage(calculateReplacementExpression((GrExpression) args[0])).get();
    }

    @RequiredReadAction
    private static String calculateReplacementExpression(GrExpression expression) {
        GrBinaryExpression exp = (GrBinaryExpression) expression;
        IElementType sign = exp.getOperationTokenType();
        GrExpression lhs = exp.getLeftOperand();
        GrExpression rhs = exp.getRightOperand();
        assert rhs != null;
        if (mPLUS == sign) {
            if (isZero(lhs)) {
                return rhs.getText();
            }
            else {
                return lhs.getText();
            }
        }

        if (mMINUS == sign) {
            return lhs.getText();
        }

        if (mSTAR == sign) {
            if (isOne(lhs)) {
                return rhs.getText();
            }
            else if (isOne(rhs)) {
                return lhs.getText();
            }
            else {
                return "0";
            }
        }

        if (mDIV == sign) {
            return lhs.getText();
        }

        return "";
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new PointlessArithmeticFix();
    }

    private static class PointlessArithmeticFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Simplify");
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrExpression expression = (GrExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class PointlessArithmeticVisitor extends BaseInspectionVisitor {
        private final TokenSet arithmeticTokens = TokenSet.create(mPLUS, mMINUS, mSTAR, mDIV);

        @Override
        @RequiredReadAction
        public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            GrExpression rhs = expression.getRightOperand();
            if (rhs == null) {
                return;
            }

            IElementType sign = expression.getOperationTokenType();
            if (!arithmeticTokens.contains(sign)) {
                return;
            }

            GrExpression lhs = expression.getLeftOperand();

            boolean isPointless;
            if (sign.equals(mPLUS)) {
                isPointless = additionExpressionIsPointless(lhs, rhs);
            }
            else if (sign.equals(mMINUS)) {
                isPointless = subtractionExpressionIsPointless(rhs);
            }
            else if (sign.equals(mSTAR)) {
                isPointless = multiplyExpressionIsPointless(lhs, rhs);
            }
            else if (sign.equals(mDIV)) {
                isPointless = divideExpressionIsPointless(rhs);
            }
            else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }

            registerError(expression);
        }
    }

    @RequiredReadAction
    private static boolean subtractionExpressionIsPointless(GrExpression rhs) {
        return isZero(rhs);
    }

    @RequiredReadAction
    private static boolean additionExpressionIsPointless(GrExpression lhs, GrExpression rhs) {
        return isZero(lhs) || isZero(rhs);
    }

    @RequiredReadAction
    private static boolean multiplyExpressionIsPointless(GrExpression lhs, GrExpression rhs) {
        return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
    }

    @RequiredReadAction
    private static boolean divideExpressionIsPointless(GrExpression rhs) {
        return isOne(rhs);
    }

    private static final Set<String> ZEROS = Set.of("0", "0x0", "0X0", "0.0", "0L", "0l", "0b0", "0B0");
    private static final Set<String> ONES = Set.of("1", "0x1", "0X1", "1.0", "1L", "1l", "0b1", "0B1");

    /**
     * @noinspection FloatingPointEquality
     */
    @RequiredReadAction
    private static boolean isZero(GrExpression expression) {
        PsiElement inner = PsiUtil.skipParentheses(expression, false);
        if (inner == null) {
            return false;
        }

        String text = inner.getText();
        return text != null && ZEROS.contains(text);
    }

    /**
     * @noinspection FloatingPointEquality
     */
    @RequiredReadAction
    private static boolean isOne(GrExpression expression) {
        PsiElement inner = PsiUtil.skipParentheses(expression, false);
        if (inner == null) {
            return false;
        }

        String text = inner.getText();
        return text != null && ONES.contains(text);
    }
}