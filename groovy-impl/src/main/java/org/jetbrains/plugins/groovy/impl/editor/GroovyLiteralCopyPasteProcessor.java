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
package org.jetbrains.plugins.groovy.impl.editor;

import com.intellij.java.impl.codeInsight.editorActions.StringLiteralCopyPasteProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.LineTokenizer;
import consulo.codeEditor.Editor;
import consulo.codeEditor.RawText;
import consulo.codeEditor.SelectionModel;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyLiteralCopyPasteProcessor extends StringLiteralCopyPasteProcessor {

  @Override
  protected boolean isCharLiteral(@Nonnull PsiElement token) {
    return false;
  }

  @Override
  protected boolean isStringLiteral(@Nonnull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && (TokenSets.STRING_LITERALS.contains(node.getElementType()) || node.getElementType() == GroovyElementTypes.GSTRING_INJECTION);
  }

  @Nullable
  protected PsiElement findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    PsiElement elementAtSelectionStart = file.findElementAt(selectionStart);
    if (elementAtSelectionStart == null) {
      return null;
    }
    IElementType elementType = elementAtSelectionStart.getNode().getElementType();
    if ((elementType == mREGEX_END || elementType == mDOLLAR_SLASH_REGEX_END || elementType == mGSTRING_END) &&
        elementAtSelectionStart.getTextOffset() == selectionStart) {
      elementAtSelectionStart = elementAtSelectionStart.getPrevSibling();
      if (elementAtSelectionStart == null) return null;
      elementType = elementAtSelectionStart.getNode().getElementType();
    }
    if (elementType == mDOLLAR) {
      elementAtSelectionStart = elementAtSelectionStart.getParent();
      elementType = elementAtSelectionStart.getNode().getElementType();
    }



    if (!isStringLiteral(elementAtSelectionStart) &&
        !isCharLiteral(elementAtSelectionStart) &&
        !(elementType == GroovyElementTypes.GSTRING_INJECTION)) {
      return null;
    }

    if (elementAtSelectionStart.getTextRange().getEndOffset() < selectionEnd) {
      final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
      if (elementAtSelectionEnd == null) {
        return null;
      }
      if (elementAtSelectionEnd.getNode().getElementType() == elementType &&
          elementAtSelectionEnd.getTextRange().getStartOffset() < selectionEnd) {
        return elementAtSelectionStart;
      }
    }

    final TextRange textRange = elementAtSelectionStart.getTextRange();

    //content elements don't have quotes, so they are shorter than whole string literals
    if (elementType == mREGEX_CONTENT ||
        elementType == mGSTRING_CONTENT ||
        elementType == mDOLLAR_SLASH_REGEX_CONTENT ||
        elementType == GroovyElementTypes.GSTRING_INJECTION) {
      selectionStart++;
      selectionEnd--;
    }
    if (selectionStart <= textRange.getStartOffset() || selectionEnd >= textRange.getEndOffset()) {
      return null;
    }
    return elementAtSelectionStart;
  }


  @Override
  protected String getLineBreaker(@Nonnull PsiElement token) {
    final String text = token.getParent().getText();
    if (text.contains("'''") || text.contains("\"\"\"")) {
      return "\n";
    }

    final IElementType type = token.getNode().getElementType();
    if (type == mGSTRING_LITERAL || type == mGSTRING_CONTENT) {
      return super.getLineBreaker(token);
    }
    if (type == mSTRING_LITERAL) {
      return super.getLineBreaker(token).replace('"', '\'');
    }

    return "\n";

  }

  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    final SelectionModel selectionModel = editor.getSelectionModel();

    // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    PsiElement token = findLiteralTokenType(file, selectionStart, selectionEnd);
    if (token == null) {
      return text;
    }

    if (isStringLiteral(token)) {
      StringBuilder buffer = new StringBuilder(text.length());
      @NonNls String breaker = getLineBreaker(token);
      final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
      for (int i = 0; i < lines.length; i++) {
        buffer.append(escapeCharCharacters(lines[i], token));
        if (i != lines.length - 1 || "\n".equals(breaker) && text.endsWith("\n")) {
          buffer.append(breaker);
        }
      }
      text = buffer.toString();
    }
    return text;
  }

  @Nonnull
  @Override
  protected String escapeCharCharacters(@Nonnull String s, @Nonnull PsiElement token) {
    IElementType tokenType = token.getNode().getElementType();

    if (tokenType == mREGEX_CONTENT || tokenType == mREGEX_LITERAL) {
      return GrStringUtil.escapeSymbolsForSlashyStrings(s);
    }

    if (tokenType == mDOLLAR_SLASH_REGEX_CONTENT || tokenType == mDOLLAR_SLASH_REGEX_LITERAL) {
      return GrStringUtil.escapeSymbolsForDollarSlashyStrings(s);
    }

    if (tokenType == mGSTRING_CONTENT || tokenType == mGSTRING_LITERAL || tokenType == GroovyElementTypes.GSTRING_INJECTION) {
      boolean singleLine = !token.getParent().getText().contains("\"\"\"");
      StringBuilder b = new StringBuilder();
      GrStringUtil.escapeStringCharacters(s.length(), s, singleLine ? "\"" : "", singleLine, true, b);
      GrStringUtil.unescapeCharacters(b, singleLine ? "'" : "'\"", true);
      for (int i = b.length() - 2; i >= 0; i--) {
        if (b.charAt(i) == '$') {
          final char next = b.charAt(i + 1);
          if (next != '{' && !Character.isLetter(next)) {
            b.insert(i, '\\');
          }
        }
      }
      if (b.charAt(b.length() - 1) == '$') {
        b.insert(b.length() - 1, '\\');
      }
      return b.toString();
    }

    if (tokenType == mSTRING_LITERAL) {
      return GrStringUtil.escapeSymbolsForString(s, !token.getText().contains("'''"), false);
    }

    return super.escapeCharCharacters(s, token);
  }

  @Nonnull
  @Override
  protected String unescape(String s, PsiElement token) {
    final IElementType tokenType = token.getNode().getElementType();

    if (tokenType == mREGEX_CONTENT || tokenType == mREGEX_LITERAL) {
      return GrStringUtil.unescapeSlashyString(s);
    }

    if (tokenType == mDOLLAR_SLASH_REGEX_CONTENT || tokenType == mDOLLAR_SLASH_REGEX_LITERAL) {
      return GrStringUtil.unescapeDollarSlashyString(s);
    }

    if (tokenType == mGSTRING_CONTENT || tokenType == mGSTRING_LITERAL) {
      return GrStringUtil.unescapeString(s);
    }

    if (tokenType == mSTRING_LITERAL) {
      return GrStringUtil.unescapeString(s);
    }

    return super.unescape(s, token);
  }

}
