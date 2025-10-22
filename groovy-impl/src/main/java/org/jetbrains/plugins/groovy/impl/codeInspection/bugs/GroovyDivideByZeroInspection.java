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

import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Set;

public class GroovyDivideByZeroInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PROBABLE_BUGS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Divide by zero");
    }

    @Nullable
    @Override
    protected String buildErrorString(Object... args) {
        return "Division by zero #loc";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        private static final Set<String> ZEROS = Set.of("0","0x0", "0X0", "0.0", "0L", "0l");
        private static final Set<IElementType> DIV_OPERATORS = Set.of(GroovyTokenTypes.mDIV, GroovyTokenTypes.mMOD);
        private static final Set<IElementType> DIV_ASSIGN_OPERATORS = Set.of(GroovyTokenTypes.mDIV_ASSIGN, GroovyTokenTypes.mMOD_ASSIGN);

        @Override
        @RequiredReadAction
        public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            GrExpression rhs = expression.getRightOperand();
            if (rhs != null && DIV_OPERATORS.contains(expression.getOperationTokenType()) && ZEROS.contains(rhs.getText())) {
                registerError(expression);
            }
        }

        @Override
        @RequiredReadAction
        public void visitAssignmentExpression(GrAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            GrExpression rhs = expression.getRValue();
            if (rhs != null && DIV_ASSIGN_OPERATORS.contains(expression.getOperationTokenType()) && ZEROS.contains(rhs.getText())) {
                registerError(expression);
            }
        }
    }
}