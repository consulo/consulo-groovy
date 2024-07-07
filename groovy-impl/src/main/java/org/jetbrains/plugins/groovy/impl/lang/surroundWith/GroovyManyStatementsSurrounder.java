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
package org.jetbrains.plugins.groovy.impl.lang.surroundWith;

import jakarta.annotation.Nonnull;

import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyManyStatementsSurrounder implements Surrounder
{

  public boolean isApplicable(@Nonnull PsiElement[] elements) {
    if (elements.length == 0) return false;

    for (PsiElement element : elements) {
      if (!isStatement(element)) return false;
    }

    if (elements[0] instanceof GrBlockStatement) {
      return false;
    }

    return true;
  }

  public static boolean isStatement(@Nonnull PsiElement element) {
    return ";".equals(element.getText()) || element instanceof PsiComment || StringUtil.isEmptyOrSpaces(element.getText()) || PsiUtil.isExpressionStatement(element);
  }

  @Nullable
  public TextRange surroundElements(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length == 0) return null;

    PsiElement element1 = elements[0];
    final GroovyPsiElement newStmt = doSurroundElements(elements, element1.getParent());
    assert newStmt != null;

    ASTNode parentNode = element1.getParent().getNode();
    if (elements.length > 1) {
      parentNode.removeRange(element1.getNode().getTreeNext(), elements[elements.length - 1].getNode().getTreeNext());
    }
    parentNode.replaceChild(element1.getNode(), newStmt.getNode());

    return getSurroundSelectionRange(newStmt);
  }

  protected static void addStatements(GrCodeBlock block, PsiElement[] elements) throws IncorrectOperationException {
    block.addRangeBefore(elements[0], elements[elements.length - 1], block.getRBrace());
  }

  protected abstract GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException;

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);
}