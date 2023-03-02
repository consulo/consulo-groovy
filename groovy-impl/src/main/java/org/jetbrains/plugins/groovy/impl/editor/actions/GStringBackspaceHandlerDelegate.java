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
import consulo.language.editor.action.BackspaceHandlerDelegate;
import consulo.language.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GStringBackspaceHandlerDelegate extends BackspaceHandlerDelegate {

  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
    if (c != '{') return;

    if (!(file instanceof GroovyFile)) return;

    final int offset = editor.getCaretModel().getOffset();

    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (offset < 1) return;

    HighlighterIterator iterator = highlighter.createIterator(offset);
    if (iterator.getTokenType() != GroovyTokenTypes.mRCURLY) return;
    iterator.retreat();
    if (iterator.getStart() < 1 || iterator.getTokenType() != GroovyTokenTypes.mLCURLY) return;

    editor.getDocument().deleteString(offset, offset + 1);
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    return false;
  }
}
