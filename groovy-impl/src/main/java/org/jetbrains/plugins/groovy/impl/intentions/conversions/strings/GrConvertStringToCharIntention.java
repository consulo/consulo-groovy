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
package org.jetbrains.plugins.groovy.impl.intentions.conversions.strings;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public class GrConvertStringToCharIntention extends Intention {
  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException
  {
    final GrExpression cast = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("a as char");
    ((GrSafeCastExpression)cast).getOperand().replaceWithExpression((GrExpression)element, true);

    ((GrExpression)element).replaceWithExpression(cast, true);
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrLiteral)) return false;

        final Object value = ((GrLiteral)element).getValue();
        return value instanceof String && ((String)value).length() == 1;
      }
    };
  }
}
