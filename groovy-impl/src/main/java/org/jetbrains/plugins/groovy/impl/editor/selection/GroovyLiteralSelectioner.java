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

package org.jetbrains.plugins.groovy.impl.editor.selection;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.ExtendWordSelectionHandlerBase;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyLiteralSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(PsiElement e) {
    PsiElement parent = e.getParent();
    return isLiteral(e) || isLiteral(parent);
  }

  private static boolean isLiteral(PsiElement element) {
    return element instanceof GrListOrMap ||
           element instanceof GrArgumentLabel ||
           element instanceof GrLiteralImpl && ((GrLiteralImpl)element).isStringLiteral();
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (e instanceof GrListOrMap) return result;

    int startOffset = -1;
    int endOffset = -1;
    final String text = e.getText();
    final int stringOffset = e.getTextOffset();
    final IElementType elementType = e.getNode().getElementType();
    if (elementType == mGSTRING_CONTENT || elementType == mREGEX_CONTENT || elementType == mDOLLAR_SLASH_REGEX_CONTENT) {
      int cur;
      int index = -1;
      while (true) {
        cur = text.indexOf('\n', index + 1);
        if (cur < 0 || cur + stringOffset > cursorOffset) break;
        index = cur;
      }
      if (index >= 0) {
        startOffset = stringOffset + index + 1;
      }

      index = text.indexOf('\n', cursorOffset - stringOffset);
      if (index >= 0) {
        endOffset = stringOffset + index + 1;
      }
    }

    if (startOffset >= 0 && endOffset >= 0) {
      result.add(new TextRange(startOffset, endOffset));
    }

    final String content = GrStringUtil.removeQuotes(text);

    final int offset = stringOffset + text.indexOf(content);
    result.add(new TextRange(offset, offset + content.length()));
    return result;
  }
}