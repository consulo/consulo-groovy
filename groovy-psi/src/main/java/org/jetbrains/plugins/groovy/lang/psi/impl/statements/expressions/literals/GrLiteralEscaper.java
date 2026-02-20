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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import jakarta.annotation.Nonnull;

import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import consulo.language.psi.LiteralTextEscaper;

public class GrLiteralEscaper extends LiteralTextEscaper<GrLiteralContainer> {
  private int[] outSourceOffsets;

  public GrLiteralEscaper(GrLiteralContainer literal) {
    super(literal);
  }

  @Override
  public boolean decode(@Nonnull TextRange rangeInsideHost, @Nonnull StringBuilder outChars) {
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length() + 1];

    IElementType elementType = myHost.getFirstChild().getNode().getElementType();
    if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_CONTENT) {
      return GrStringUtil.parseStringCharacters(subText, outChars, outSourceOffsets);
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mREGEX_CONTENT) {
      return GrStringUtil.parseRegexCharacters(subText, outChars, outSourceOffsets, true);
    }
    else if (elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT) {
      return GrStringUtil.parseRegexCharacters(subText, outChars, outSourceOffsets, false);
    }
    else return false;
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @Nonnull TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    Object value = myHost.getValue();
    return value instanceof String && ((String)value).indexOf('\n') < 0;
  }
}
