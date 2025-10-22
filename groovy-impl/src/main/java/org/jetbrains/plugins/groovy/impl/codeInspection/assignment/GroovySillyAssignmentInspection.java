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
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

@ExtensionImpl
public class GroovySillyAssignmentInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return ASSIGNMENT_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Silly assignment");
    }

    @Override
    @Nullable
    protected String buildErrorString(Object... args) {
        return "Silly assignment #loc";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        @Override
        public void visitAssignmentExpression(@Nonnull GrAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);

            IElementType sign = assignment.getOperationTokenType();
            if (!sign.equals(GroovyTokenTypes.mASSIGN)) {
                return;
            }
            GrExpression lhs = assignment.getLValue();
            GrExpression rhs = assignment.getRValue();
            if (rhs == null) {
                return;
            }
            if (!(rhs instanceof GrReferenceExpression rhsRef) || !(lhs instanceof GrReferenceExpression lhsRef)) {
                return;
            }
            GrExpression rhsQualifier = rhsRef.getQualifierExpression();
            GrExpression lhsQualifier = lhsRef.getQualifierExpression();
            if (rhsQualifier != null || lhsQualifier != null) {
                if (!EquivalenceChecker.expressionsAreEquivalent(rhsQualifier, lhsQualifier)) {
                    return;
                }
            }
            String rhsName = rhsRef.getReferenceName();
            String lhsName = lhsRef.getReferenceName();
            if (rhsName == null || lhsName == null) {
                return;
            }
            if (!rhsName.equals(lhsName)) {
                return;
            }
            PsiElement rhsReferent = rhsRef.resolve();
            PsiElement lhsReferent = lhsRef.resolve();
            if (rhsReferent == null || lhsReferent == null || !rhsReferent.equals(lhsReferent)) {
                return;
            }
            registerError(assignment);
        }
    }
}
