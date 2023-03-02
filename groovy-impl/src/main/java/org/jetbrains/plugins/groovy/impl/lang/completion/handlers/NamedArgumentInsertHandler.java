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
package org.jetbrains.plugins.groovy.impl.lang.completion.handlers;

import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.Lookup;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Maxim.Medvedev
 */
public class NamedArgumentInsertHandler implements InsertHandler<LookupElement> {

  public static final NamedArgumentInsertHandler INSTANCE = new NamedArgumentInsertHandler();

  private NamedArgumentInsertHandler() {}

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    int tailOffset = context.getTailOffset();

    PsiElement argumentList = context.getFile().findElementAt(tailOffset - 1);
    while (argumentList != null && !(argumentList instanceof GrArgumentList) && !(argumentList instanceof GrListOrMap)) {
      argumentList = argumentList.getParent();
    }

    final Editor editor = context.getEditor();

    if (argumentList != null) {
      CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(context.getProject()).getCurrentSettings();
      GroovyCodeStyleSettings codeStyleSettings = settings.getCustomSettings(GroovyCodeStyleSettings.class);
      CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(GroovyLanguage.INSTANCE);

      boolean insertSpace = codeStyleSettings.SPACE_IN_NAMED_ARGUMENT;

      if (context.getCompletionChar() == ':' || (insertSpace && context.getCompletionChar() == ' ')) {
        context.setAddCompletionChar(false);
      }

      String argumentListText = argumentList.getText();

      String s = argumentListText.substring(tailOffset - argumentList.getTextOffset());
      s = StringUtil.trimEnd(s, ")");

      if (s.trim().isEmpty()) {
        String toInsert = insertSpace ? ": " : ":";
        editor.getDocument().insertString(tailOffset, toInsert);
        editor.getCaretModel().moveToOffset(tailOffset + toInsert.length());
      }
      else {
        if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
          char a = s.charAt(0);
          if (Character.isLetterOrDigit(a)) {
            return;
          }
        }

        Matcher m = Pattern.compile("([ \\t]*):([ \\t]*)(.*)", Pattern.DOTALL).matcher(s);
        if (m.matches()) {
          int caret = tailOffset + m.end(2);

          if (m.group(2).isEmpty()) {
            editor.getDocument().insertString(caret, " ");
            caret++;
          }

          editor.getCaretModel().moveToOffset(caret);
        }
        else {
          m = Pattern.compile("([ \\t]*)([\\n \\t]*)[\\],](.*)", Pattern.DOTALL).matcher(s);
          if (m.matches()) {
            String toInsert = insertSpace ? ": " : ":";
            editor.getDocument().replaceString(tailOffset, tailOffset + m.start(2), toInsert);
            editor.getCaretModel().moveToOffset(tailOffset + toInsert.length());
          }
          else {
            m = Pattern.compile("([ \\t]*)(.*)", Pattern.DOTALL).matcher(s);
            if (!m.matches()) throw new RuntimeException("This pattern must match any non-empty string! (" + s + ")");

            StringBuilder sb = new StringBuilder(3);
            sb.append(':');
            int shiftCaret = 1;
            if (insertSpace) {
              sb.append(' ');
              shiftCaret++;
            }

            if (!m.group(2).startsWith("\n") && commonCodeStyleSettings.SPACE_AFTER_COMMA) {
              sb.append(' ');
            }

            editor.getDocument().replaceString(tailOffset, tailOffset + m.start(2), sb);
            editor.getCaretModel().moveToOffset(tailOffset + shiftCaret);
          }
        }
      }

      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }
}
