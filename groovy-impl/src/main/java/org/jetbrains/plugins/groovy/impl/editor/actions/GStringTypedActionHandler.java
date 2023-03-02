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

package org.jetbrains.plugins.groovy.impl.editor.actions;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorHighlighter;
import consulo.codeEditor.HighlighterIterator;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GStringTypedActionHandler extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, Project project, Editor editor, @Nonnull PsiFile file) {
    if (c != '{' || project == null || !HandlerUtils.canBeInvoked(editor, project)) {
      return Result.CONTINUE;
    }

    if (!(file instanceof GroovyFile)) return Result.CONTINUE;

    int caret = editor.getCaretModel().getOffset();
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (caret < 1) return Result.CONTINUE;

    HighlighterIterator iterator = highlighter.createIterator(caret - 1);
    if (iterator.getTokenType() != GroovyTokenTypes.mLCURLY) return Result.CONTINUE;
    iterator.retreat();
    if (iterator.atEnd() || iterator.getTokenType() != GroovyTokenTypes.mDOLLAR) return Result.CONTINUE;
    iterator.advance();
    if (iterator.atEnd()) return Result.CONTINUE;
    iterator.advance();
    if (iterator.getTokenType() != GroovyTokenTypes.mGSTRING_BEGIN) return Result.CONTINUE;

    editor.getDocument().insertString(caret, "}");
    return Result.STOP;
  }
}
