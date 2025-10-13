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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GroovyAssignmentToMethodParameterInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return ASSIGNMENT_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Assignment to method parameter");
    }

    @Override
    @Nullable
    protected String buildErrorString(Object... args) {
        return "Assignment to method parameter '#ref' #loc";

    }

    @Override
    @Nonnull
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {

        @Override
        public void visitAssignmentExpression(GrAssignmentExpression expr) {
            super.visitAssignmentExpression(expr);

            check(expr.getLValue());
        }

        private void check(@Nullable GrExpression lhs) {
            if (!(lhs instanceof GrReferenceExpression)) {
                return;
            }
            final PsiElement referent = ((PsiReference) lhs).resolve();
            if (referent == null) {
                return;
            }
            if (!(referent instanceof GrParameter)) {
                return;
            }
            if (referent.getParent() instanceof GrForClause) {
                return;
            }
            registerError(lhs);
        }

        @Override
        public void visitUnaryExpression(GrUnaryExpression expression) {
            super.visitUnaryExpression(expression);

            final IElementType op = expression.getOperationTokenType();
            if (op == GroovyTokenTypes.mINC || op == GroovyTokenTypes.mDEC) {
                check(expression.getOperand());
            }
        }
    }
}
