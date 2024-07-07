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
package org.jetbrains.plugins.groovy.impl.editor.actions.joinLines;


import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.document.Document;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.JoinRawLinesHandlerDelegate;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.ast.TokenType;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

public abstract class GrJoinLinesHandlerBase implements JoinRawLinesHandlerDelegate
{
  private static final boolean BACK = false;
  private static final boolean FORWARD = false;

  @Override
  public int tryJoinRawLines(Document document, PsiFile file, int start, int end) {
    if (!(file instanceof GroovyFileBase)) return CANNOT_JOIN;
    final PsiElement element = file.findElementAt(end);
    final GrStatementOwner statementOwner = PsiTreeUtil.getParentOfType(element, GrStatementOwner.class, true, GroovyFileBase.class);
    if (statementOwner == null) return CANNOT_JOIN;

    GrStatement first = null;
    GrStatement last = null;
    for (PsiElement child = statementOwner.getFirstChild(); child != null; child = child.getNextSibling()) {
      final TextRange range = child.getTextRange();
      if (range.getEndOffset() == start) {
        first = skipSemicolonsAndWhitespaces(child, BACK);
      }
      else if (range.getStartOffset() == end) {
        last = skipSemicolonsAndWhitespaces(child, FORWARD);
      }

    }
    if (last == null || first == null) return CANNOT_JOIN;
    return tryJoinStatements(first, last);
  }

  @Override
  public int tryJoinLines(Document document, PsiFile file, int start, int end) {
    return CANNOT_JOIN;
  }

  @Nullable
  private static GrStatement skipSemicolonsAndWhitespaces(PsiElement child, boolean forward) {
    while (child != null && !(child instanceof GrStatement)) {
      final IElementType type = child.getNode().getElementType();
      if (type != GroovyTokenTypes.mSEMI && !(type == TokenType.WHITE_SPACE && !child.getText().contains("\n"))) return null;
      child = forward ? child.getNextSibling() : child.getPrevSibling();
    }
    return (GrStatement)child;
  }

  public abstract int tryJoinStatements(@Nonnull GrStatement first, @Nonnull GrStatement second);
}
