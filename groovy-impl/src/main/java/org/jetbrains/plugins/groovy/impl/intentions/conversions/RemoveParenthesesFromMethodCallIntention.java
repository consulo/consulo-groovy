/*
 * Copyright 2008 Bas Leijdekkers
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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

public class RemoveParenthesesFromMethodCallIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.removeParenthesesFromMethodCallIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new RemoveParenthesesFromMethodPredicate();
    }

    @Override
    protected boolean isStopElement(PsiElement element) {
        return super.isStopElement(element) || element instanceof GrStatementOwner;
    }

    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrMethodCallExpression expression = (GrMethodCallExpression) element;
        StringBuilder newStatementText = new StringBuilder();
        newStatementText.append(expression.getInvokedExpression().getText()).append(' ');
        GrArgumentList argumentList = expression.getArgumentList();
        if (argumentList != null) {
            PsiElement leftParen = argumentList.getLeftParen();
            PsiElement rightParen = argumentList.getRightParen();
            if (leftParen != null) {
                leftParen.delete();
            }
            if (rightParen != null) {
                rightParen.delete();
            }
            newStatementText.append(argumentList.getText());
        }
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
        GrStatement newStatement = factory.createStatementFromText(newStatementText.toString());
        expression.replaceWithStatement(newStatement);
    }
}
