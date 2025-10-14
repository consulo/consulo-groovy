/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Iterator;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrUnusedIncDecInspection extends BaseInspection {
    private static final Logger LOG = Logger.getInstance(GrUnusedIncDecInspection.class);

    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new GrUnusedIncDecInspectionVisitor();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return GroovyInspectionLocalize.groovyDfaIssues();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.unusedIncDec();
    }

    @Nonnull
    public String getShortName() {
        return "GroovyUnusedIncOrDec";
    }

    private static class GrUnusedIncDecInspectionVisitor extends BaseInspectionVisitor {
        @Override
        public void visitUnaryExpression(GrUnaryExpression expression) {
            super.visitUnaryExpression(expression);

            IElementType opType = expression.getOperationTokenType();
            if (opType != GroovyTokenTypes.mINC && opType != GroovyTokenTypes.mDEC) {
                return;
            }

            GrExpression operand = expression.getOperand();
            if (!(operand instanceof GrReferenceExpression)) {
                return;
            }

            PsiElement resolved = ((GrReferenceExpression) operand).resolve();
            if (!(resolved instanceof GrVariable) || resolved instanceof GrField) {
                return;
            }

            final GrControlFlowOwner owner = ControlFlowUtils.findControlFlowOwner(expression);
            assert owner != null;
            GrControlFlowOwner ownerOfDeclaration = ControlFlowUtils.findControlFlowOwner(resolved);
            if (ownerOfDeclaration != owner) {
                return;
            }

            final Instruction cur = ControlFlowUtils.findInstruction(operand, owner.getControlFlow());

            if (cur == null) {
                LOG.error(
                    "no instruction found in flow." + "operand: " + operand.getText(),
                    AttachmentFactory.get().create("owner.txt", owner.getText())
                );
            }

            //get write access for inc or dec
            Iterable<? extends Instruction> successors = cur.allSuccessors();
            Iterator<? extends Instruction> iterator = successors.iterator();
            LOG.assertTrue(iterator.hasNext());
            Instruction writeAccess = iterator.next();
            LOG.assertTrue(!iterator.hasNext());

            List<ReadWriteVariableInstruction> accesses = ControlFlowUtils.findAccess((GrVariable) resolved, true, false, writeAccess);

            boolean allAreWrite = true;
            for (ReadWriteVariableInstruction access : accesses) {
                if (!access.isWrite()) {
                    allAreWrite = false;
                    break;
                }
            }


            if (allAreWrite) {
                if (expression.isPostfix() && PsiUtil.isExpressionUsed(expression)) {
                    problemsHolder.newProblem(GroovyInspectionLocalize.unused0(expression.getOperationToken().getText()))
                        .range(expression.getOperationToken())
                        .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        .withFixes(new ReplacePostfixIncWithPrefixFix(expression), new RemoveIncOrDecFix(expression))
                        .create();
                }
                else if (!PsiUtil.isExpressionUsed(expression)) {
                    problemsHolder.newProblem(GroovyInspectionLocalize.unused0(expression.getOperationToken().getText()))
                        .range(expression.getOperationToken())
                        .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        .create();
                }
            }
        }

        private static class RemoveIncOrDecFix implements LocalQuickFix {
            @Nonnull
            private final LocalizeValue myMessage;

            public RemoveIncOrDecFix(GrUnaryExpression expression) {
                myMessage = GroovyInspectionLocalize.remove0(expression.getOperationToken().getText());
            }

            @Nonnull
            @Override
            public LocalizeValue getName() {
                return myMessage;
            }

            @Override
            public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
                GrUnaryExpression expr = findUnaryExpression(descriptor);
                if (expr == null) {
                    return;
                }

                expr.replaceWithExpression(expr.getOperand(), true);
            }
        }

        private static class ReplacePostfixIncWithPrefixFix implements LocalQuickFix {
            @Nonnull
            private final LocalizeValue myMessage;

            public ReplacePostfixIncWithPrefixFix(GrUnaryExpression expression) {
                myMessage = GroovyInspectionLocalize.replacePostfix0WithPrefix0(expression.getOperationToken().getText());
            }

            @Nonnull
            @Override
            public LocalizeValue getName() {
                return myMessage;
            }

            @Override
            public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
                GrUnaryExpression expr = findUnaryExpression(descriptor);
                if (expr == null) {
                    return;
                }

                GrExpression prefix = GroovyPsiElementFactory.getInstance(project)
                    .createExpressionFromText(expr.getOperationToken().getText() + expr.getOperand().getText());

                expr.replaceWithExpression(prefix, true);
            }
        }

        private static class ReplaceIncDecWithBinary implements LocalQuickFix {
            private final LocalizeValue myMessage;

            public ReplaceIncDecWithBinary(GrUnaryExpression expression) {
                String opToken = expression.getOperationToken().getText();
                myMessage = GroovyInspectionLocalize.replace0With1(opToken, opToken.substring(0, 1));
            }

            @Nonnull
            @Override
            public LocalizeValue getName() {
                return myMessage;
            }

            @Override
            public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
                GrUnaryExpression expr = findUnaryExpression(descriptor);
                GrExpression newExpr = GroovyPsiElementFactory.getInstance(project)
                    .createExpressionFromText(expr.getOperand().getText() + expr.getOperationToken().getText().substring(0, 1) + "1");
                expr.replaceWithExpression(newExpr, true);
            }
        }
    }

    @Nullable
    private static GrUnaryExpression findUnaryExpression(ProblemDescriptor descriptor) {
        GrUnaryExpression expr;
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return null;
        }
        PsiElement parent = element.getParent();
        IElementType opType = element.getNode().getElementType();
        if (opType != GroovyTokenTypes.mINC && opType != GroovyTokenTypes.mDEC) {
            return null;
        }
        if (!(parent instanceof GrUnaryExpression)) {
            return null;
        }
        expr = (GrUnaryExpression) parent;
        return expr;
    }
}

