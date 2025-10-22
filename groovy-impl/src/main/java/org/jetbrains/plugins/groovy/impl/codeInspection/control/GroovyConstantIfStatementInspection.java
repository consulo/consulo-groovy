/*
 * Copyright 2007-2008 Dave Griffith, Bas Leijdekkers
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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyConstantIfStatementInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Constant if statement");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... args) {
        return "#ref statement can be simplified #loc";
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantIfStatementVisitor();
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new ConstantIfStatementFix();
    }

    private static class ConstantIfStatementFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Simplify");
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement ifKeyword = descriptor.getPsiElement();
            GrIfStatement ifStatement = (GrIfStatement) ifKeyword.getParent();
            assert ifStatement != null;
            GrStatement thenBranch = ifStatement.getThenBranch();
            GrStatement elseBranch = ifStatement.getElseBranch();
            GrExpression condition = ifStatement.getCondition();
            // todo still needs some handling for conflicting declarations
            if (isFalse(condition)) {
                if (elseBranch != null) {
                    replaceStatement(ifStatement, (GrStatement) elseBranch.copy());
                }
                else {
                    ifStatement.delete();
                }
            }
            else {
                replaceStatement(ifStatement, (GrStatement) thenBranch.copy());
            }
        }
    }

    private static class ConstantIfStatementVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitIfStatement(GrIfStatement statement) {
            super.visitIfStatement(statement);
            GrExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            GrStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            if (isTrue(condition) || isFalse(condition)) {
                registerStatementError(statement);
            }
        }
    }

    @RequiredReadAction
    private static boolean isFalse(GrExpression expression) {
        return "false".equals(expression.getText());
    }

    @RequiredReadAction
    private static boolean isTrue(GrExpression expression) {
        return "true".equals(expression.getText());
    }
}