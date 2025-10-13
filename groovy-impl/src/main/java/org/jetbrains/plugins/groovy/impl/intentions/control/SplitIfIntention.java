/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Brice Dutheil
 * @author Hamlet D'Arcy
 */
public class SplitIfIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.splitIfIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement andElement, Project project, Editor editor) throws IncorrectOperationException {
        GrBinaryExpression binaryExpression = (GrBinaryExpression) andElement.getParent();
        GrIfStatement ifStatement = (GrIfStatement) binaryExpression.getParent();

        GrExpression leftOperand = binaryExpression.getLeftOperand();
        GrExpression rightOperand = binaryExpression.getRightOperand();

        GrStatement thenBranch = ifStatement.getThenBranch();

        assert thenBranch != null;
        assert rightOperand != null;
        GrStatement newSplittedIfs = GroovyPsiElementFactory.getInstance(project)
            .createStatementFromText(
                "if(" + leftOperand.getText() +
                    ") { \n" +
                    "  if(" + rightOperand.getText() + ")" +
                    thenBranch.getText() + "\n" +
                    "}"
            );

        ifStatement.replaceWithStatement(newSplittedIfs);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {
            @Override
            public boolean satisfiedBy(PsiElement element) {
                return element.getParent() instanceof GrBinaryExpression &&
                    ((GrBinaryExpression) element.getParent()).getRightOperand() != null &&
                    element.getParent().getParent() instanceof GrIfStatement &&
                    ((GrIfStatement) element.getParent().getParent()).getElseBranch() == null
                    && "&&".equals(element.getText());
            }
        };
    }
}
