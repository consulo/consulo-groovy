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
package org.jetbrains.plugins.groovy.impl.editor.actions;

import com.intellij.java.impl.codeInsight.editorActions.JavaTypedHandler;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.HighlighterIterator;
import consulo.language.ast.TokenSet;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyTypedHandler extends TypedHandlerDelegate {
  static final TokenSet INVALID_INSIDE_REFERENCE =
    TokenSet.create(GroovyTokenTypes.mSEMI, GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY);
  private boolean myJavaLTTyped;

  public Result beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
      file instanceof GroovyFile &&
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
      isAfterClassLikeIdentifier(offsetBefore, editor);

    if ('>' == c) {
      if (file instanceof GroovyFile && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
        if (JavaTypedHandler.handleJavaGT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE)) return Result.STOP;
      }
    }

    if (c == '@' && file instanceof GroovyFile) {
      autoPopupMemberLookup(project, editor, new Condition<PsiFile>() {
        public boolean value(final PsiFile file) {
          int offset = editor.getCaretModel().getOffset();

          PsiElement lastElement = file.findElementAt(offset - 1);
          if (lastElement == null) return false;

          final PsiElement prevSibling = PsiTreeUtil.prevVisibleLeaf(lastElement);
          return prevSibling != null && ".".equals(prevSibling.getText());
        }
      });
    }

    if (c == '&' && file instanceof GroovyFile) {
      autoPopupMemberLookup(project, editor, new Condition<PsiFile>() {
        public boolean value(final PsiFile file) {
          int offset = editor.getCaretModel().getOffset();

          PsiElement lastElement = file.findElementAt(offset - 1);
          return lastElement != null && ".&".equals(lastElement.getText());
        }
      });
    }


    return Result.CONTINUE;
  }

  private static void autoPopupMemberLookup(Project project, final Editor editor, Condition<PsiFile> condition) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, condition);
  }


  public Result charTyped(final char c, final Project project, final Editor editor, @Nonnull final PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      JavaTypedHandler.handleAfterJavaLT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  public static boolean isAfterClassLikeIdentifier(final int offset, final Editor editor) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;
    if (iterator.getStart() > 0) iterator.retreat();
    return JavaTypedHandler.isClassLikeIdentifier(offset, editor, iterator, GroovyTokenTypes.mIDENT);
  }


}
