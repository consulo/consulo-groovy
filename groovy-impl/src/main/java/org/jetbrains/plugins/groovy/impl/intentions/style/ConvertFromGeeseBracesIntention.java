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
package org.jetbrains.plugins.groovy.impl.intentions.style;

import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.ast.IElementType;
import consulo.language.util.IncorrectOperationException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.impl.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

/**
 * @author Max Medvedev
 */
public class ConvertFromGeeseBracesIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ConvertFromGeeseBracesIntention.class);

  private static final PsiElementPredicate MY_PREDICATE = new PsiElementPredicate() {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      if (element.getLanguage() != GroovyFileType.GROOVY_LANGUAGE) return false;
      if (!CodeStyleSettingsManager.getInstance(element.getProject()).getCurrentSettings()
        .getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES) {
        return false;
      }

      IElementType elementType = element.getNode().getElementType();
      if (TokenSets.WHITE_SPACES_SET.contains(elementType)) {
        element = PsiTreeUtil.prevLeaf(element);
      }

      if (!GeeseUtil.isClosureRBrace(element)) return false;

      String text = element.getContainingFile().getText();

      PsiElement first = element;
      PsiElement last = element;
      for (PsiElement cur = getNext(element); GeeseUtil.isClosureRBrace(cur); cur = getNext(cur)) {
        if (!StringUtil.contains(text, last.getTextRange().getEndOffset(), cur.getTextRange().getStartOffset(), '\n')) return true;
        last = cur;
      }

      for (PsiElement cur = getPrev(element); GeeseUtil.isClosureRBrace(cur); cur = getPrev(cur)) {
        if (!StringUtil.contains(text, cur.getTextRange().getEndOffset(), first.getTextRange().getStartOffset(), '\n')) return true;
        first = cur;
      }

      return false;
    }
  };

  @Nullable
  private static PsiElement getPrev(PsiElement element) {
    PsiElement prev = GeeseUtil.getPreviousNonWhitespaceToken(element);
    if (prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mNLS) {
      prev = GeeseUtil.getPreviousNonWhitespaceToken(prev);
    }
    return prev;
  }

  @Nullable
  private static PsiElement getNext(PsiElement element) {
    PsiElement next = GeeseUtil.getNextNonWhitespaceToken(element);
    if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mNLS) next = GeeseUtil.getNextNonWhitespaceToken(next);
    return next;
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    IElementType elementType = element.getNode().getElementType();
    if (TokenSets.WHITE_SPACES_SET.contains(elementType)) {
      element = PsiTreeUtil.prevLeaf(element);
    }
    LOG.assertTrue(GeeseUtil.isClosureRBrace(element));

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = element.getContainingFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);

    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement cur = element; GeeseUtil.isClosureRBrace(cur); cur = getNext(cur)) {
      last = cur;
    }

    for (PsiElement cur = element; GeeseUtil.isClosureRBrace(cur); cur = getPrev(cur)) {
      first = cur;
    }

    LOG.assertTrue(first != null);
    LOG.assertTrue(last != null);


    RangeMarker rangeMarker = document.createRangeMarker(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());

    String text = document.getText();
    for (PsiElement cur = getPrev(last); GeeseUtil.isClosureRBrace(cur); cur = getPrev(cur)) {
      int offset = last.getTextRange().getStartOffset();
      if (!StringUtil.contains(text, cur.getTextRange().getEndOffset(), offset, '\n')) {
        document.insertString(offset, "\n");
      }
      last = cur;
    }


    CodeStyleManager.getInstance(project).reformatText(file, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
  }


  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return MY_PREDICATE;
  }
}
