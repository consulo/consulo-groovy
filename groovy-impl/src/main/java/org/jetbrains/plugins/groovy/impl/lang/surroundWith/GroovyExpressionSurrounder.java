/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.surroundWith;

import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nonnull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyExpressionSurrounder implements Surrounder
{
  protected boolean isApplicable(@Nonnull PsiElement element) {
    return element instanceof GrExpression;
  }

  @Nullable
  public TextRange surroundElements(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements) throws IncorrectOperationException
  {
    if (elements.length != 1) return null;

    PsiElement element = elements[0];

    return surroundExpression((GrExpression) element, element.getParent());
  }

  protected abstract TextRange surroundExpression(GrExpression expression, PsiElement context);

  public boolean isApplicable(@Nonnull PsiElement[] elements) {
    return elements.length == 1 &&  isApplicable(elements[0]);
  }

  protected static void replaceToOldExpression(GrExpression oldExpr, GrExpression replacement) {
    oldExpr.replaceWithExpression(replacement, false);
  }
}
