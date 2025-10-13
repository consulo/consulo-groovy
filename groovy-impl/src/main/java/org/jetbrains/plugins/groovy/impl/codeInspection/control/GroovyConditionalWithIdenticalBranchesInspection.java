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
package org.jetbrains.plugins.groovy.impl.codeInspection.control;

import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyConditionalWithIdenticalBranchesInspection extends BaseInspection {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Conditional expression with identical branches");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    public String buildErrorString(Object... args) {
        return "Conditional expression with identical branches #loc";
    }

    public GroovyFix buildFix(PsiElement location) {
        return new CollapseConditionalFix();
    }

    private static class CollapseConditionalFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Collapse conditional expression");
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof GrConditionalExpression)) {
                return;
            }
            final GrConditionalExpression expression = (GrConditionalExpression) element;
            final GrExpression thenBranch = expression.getThenBranch();
            replaceExpression(expression, thenBranch.getText());
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        public void visitConditionalExpression(GrConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final GrExpression thenBranch = expression.getThenBranch();
            final GrExpression elseBranch = expression.getElseBranch();
            if (thenBranch == null || elseBranch == null) {
                return;
            }
            if (EquivalenceChecker.expressionsAreEquivalent(thenBranch, elseBranch)) {
                registerStatementError(expression);
            }
        }
    }
}