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

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyReturnFromClosureCanBeImplicitInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("'return' statement can be implicit");
    }

    @Nullable
    protected String buildErrorString(Object... args) {
        return "#ref statement at end of a closure can be made implicit #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    @Nullable
    protected GroovyFix buildFix(PsiElement location) {
        return new MakeReturnImplicitFix();
    }

    private static class MakeReturnImplicitFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Make return implicit");
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement returnKeywordElement = descriptor.getPsiElement();
            GrReturnStatement returnStatement = (GrReturnStatement) returnKeywordElement.getParent();
            if (returnStatement == null) {
                return;
            }
            if (returnStatement.getReturnValue() == null) {
                return;
            }
            replaceStatement(returnStatement, returnStatement.getReturnValue().getText());
        }
    }

    private static class Visitor extends BaseInspectionVisitor {
        public void visitReturnStatement(GrReturnStatement returnStatement) {
            super.visitReturnStatement(returnStatement);
            GrExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            GrClosableBlock closure = PsiTreeUtil.getParentOfType(returnStatement, GrClosableBlock.class);
            if (closure == null) {
                return;
            }
            GrMethod containingMethod = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class);
            if (containingMethod != null && PsiTreeUtil.isAncestor(closure, containingMethod, true)) {
                return;
            }
            if (!ControlFlowUtils.closureCompletesWithStatement(closure, returnStatement)) {
                return;
            }
            registerStatementError(returnStatement);
        }
    }
}