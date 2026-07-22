/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.control;

import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Max Medvedev
 */
public class ReplaceIfWithTernaryIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.replaceIfWithTernaryIntentionName();
    }

    @Override
    @RequiredWriteAction
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrIfStatement ifStatement = (GrIfStatement) element.getParent();

        PsiElement thenBranch = skipBlock(ifStatement.getThenBranch());
        PsiElement elseBranch = skipBlock(ifStatement.getElseBranch());

        if (thenBranch instanceof GrAssignmentExpression thenAssignment && elseBranch instanceof GrAssignmentExpression elseAssignment) {
            GrAssignmentExpression assignment =
                (GrAssignmentExpression) GroovyPsiElementFactory.getInstance(project).createStatementFromText("a = b ? c : d");

            assignment.getLValue().replaceWithExpression(thenAssignment.getLValue(), true);

            GrConditionalExpression conditional = (GrConditionalExpression) assignment.getRValue();
            replaceConditional(
                conditional,
                ifStatement.getCondition(),
                thenAssignment.getRValue(),
                elseAssignment.getRValue()
            );
            ifStatement.replaceWithStatement(assignment);
        }

        if (thenBranch instanceof GrReturnStatement thenReturn && elseBranch instanceof GrReturnStatement elseReturn) {
            GrReturnStatement returnSt =
                (GrReturnStatement) GroovyPsiElementFactory.getInstance(project).createStatementFromText("return a ? b : c");
            GrConditionalExpression conditional = (GrConditionalExpression) returnSt.getReturnValue();
            replaceConditional(
                conditional,
                ifStatement.getCondition(),
                thenReturn.getReturnValue(),
                elseReturn.getReturnValue()
            );

            ifStatement.replaceWithStatement(returnSt);
        }
    }

    @RequiredWriteAction
    @SuppressWarnings("ConstantConditions")
    private static void replaceConditional(
        GrConditionalExpression conditional,
        GrExpression condition,
        GrExpression then,
        GrExpression elze
    ) {
        conditional.getCondition().replaceWithExpression(condition, true);
        conditional.getThenBranch().replaceWithExpression(then, true);
        conditional.getElseBranch().replaceWithExpression(elze, true);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return e -> {
            if (!e.getNode().getElementType().equals(GroovyTokenTypes.kIF)) {
                return false;
            }

            GrIfStatement ifStatement = (GrIfStatement) e.getParent();
            PsiElement thenBranch = skipBlock(ifStatement.getThenBranch());
            PsiElement elseBranch = skipBlock(ifStatement.getElseBranch());

            if (thenBranch instanceof GrAssignmentExpression thenAssignment
                && elseBranch instanceof GrAssignmentExpression elseAssignment
                && thenAssignment.getRValue() != null
                && elseAssignment.getRValue() != null) {
                return EquivalenceChecker.expressionsAreEquivalent(thenAssignment.getLValue(), elseAssignment.getLValue());
            }

            return thenBranch instanceof GrReturnStatement thenReturn
                && elseBranch instanceof GrReturnStatement elseReturn
                && thenReturn.getReturnValue() != null
                && elseReturn.getReturnValue() != null;
        };
    }

    private static PsiElement skipBlock(PsiElement e) {
        if (e instanceof GrBlockStatement blockStmt && blockStmt.getBlock().getStatements().length == 1) {
            return blockStmt.getBlock().getStatements()[0];
        }
        else {
            return e;
        }
    }
}
