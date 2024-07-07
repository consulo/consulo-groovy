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

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

public class RemoveParenthesesFromMethodCallIntention extends Intention {

  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new RemoveParenthesesFromMethodPredicate();
  }

  @Override
  protected boolean isStopElement(PsiElement element) {
    return super.isStopElement(element) || element instanceof GrStatementOwner;
  }

  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException
  {
    final GrMethodCallExpression expression = (GrMethodCallExpression) element;
    final StringBuilder newStatementText = new StringBuilder();
    newStatementText.append(expression.getInvokedExpression().getText()).append(' ');
    final GrArgumentList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final PsiElement leftParen = argumentList.getLeftParen();
      final PsiElement rightParen = argumentList.getRightParen();
      if (leftParen != null) leftParen.delete();
      if (rightParen != null) rightParen.delete();
      newStatementText.append(argumentList.getText());
    }
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrStatement newStatement = factory.createStatementFromText(newStatementText.toString());
    expression.replaceWithStatement(newStatement);
  }
}
