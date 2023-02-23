/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.javadoc.PsiInlineDocTag;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.ide.impl.idea.openapi.editor.EditorModificationUtil;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.Lookup;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.CharArrayUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;

/**
 * @author Max Medvedev
 */
public class GroovyMethodSignatureInsertHandler implements InsertHandler<LookupElement> {
  private static final Logger LOG = Logger.getInstance(GroovyMethodSignatureInsertHandler.class);

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    if (!(item.getObject() instanceof PsiMethod)) {
      return;
    }
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
    final Editor editor = context.getEditor();
    final PsiMethod method = (PsiMethod)item.getObject();

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final StringBuilder buffer = new StringBuilder();

    final CharSequence chars = editor.getDocument().getCharsSequence();
    int endOffset = editor.getCaretModel().getOffset();
    final Project project = context.getProject();
    int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset - 1, "#") + 1;
    int signatureOffset = afterSharp;

    PsiElement element = context.getFile().findElementAt(signatureOffset - 1);
    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(element.getProject());
    PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
    if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
      final PsiDocTagValue value = tag.getValueElement();
      endOffset = value.getTextRange().getEndOffset();
    }
    editor.getDocument().deleteString(afterSharp, endOffset);
    editor.getCaretModel().moveToOffset(signatureOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
    buffer.append(method.getName()).append("(");
    final int afterParenth = afterSharp + buffer.length();
    for (int i = 0; i < parameters.length; i++) {
      final PsiType type = TypeConversionUtil.erasure(parameters[i].getType());
      buffer.append(type.getCanonicalText());

      if (i < parameters.length - 1) {
        buffer.append(",");
        if (styleSettings.SPACE_AFTER_COMMA) buffer.append(" ");
      }
    }
    buffer.append(")");
    if (!(tag instanceof PsiInlineDocTag)) {
      buffer.append(" ");
    }
    else {
      final int currentOffset = editor.getCaretModel().getOffset();
      if (chars.charAt(currentOffset) == '}') {
        afterSharp++;
      }
      else {
        buffer.append("} ");
      }
    }
    String insertString = buffer.toString();
    EditorModificationUtil.insertStringAtCaret(editor, insertString);
    editor.getCaretModel().moveToOffset(afterSharp + buffer.length());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    shortenReferences(project, editor, context, afterParenth);
  }

  private static void shortenReferences(final Project project, final Editor editor, InsertionContext context, int offset) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final PsiElement element = context.getFile().findElementAt(offset);
    final GrDocMemberReference tagValue = PsiTreeUtil.getParentOfType(element, GrDocMemberReference.class);
    if (tagValue != null) {
      try {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(tagValue);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
  }
}
