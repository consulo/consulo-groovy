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

package org.jetbrains.plugins.groovy.highlighter;

import consulo.language.BracePair;
import consulo.language.Language;
import consulo.language.PairedBraceMatcher;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static consulo.language.ast.TokenType.WHITE_SPACE;
import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.COMMENT_SET;

/**
 * @author ilyas
 */
public class GroovyBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = {
    new BracePair(mLPAREN, mRPAREN, false),
    new BracePair(mLBRACK, mRBRACK, false),
    new BracePair(mLCURLY, mRCURLY, true),

    new BracePair(mGDOC_INLINE_TAG_START, mGDOC_INLINE_TAG_END, false),
    new BracePair(mGDOC_TAG_VALUE_LPAREN, mGDOC_TAG_VALUE_RPAREN, false),

    new BracePair(mGSTRING_BEGIN, mGSTRING_END, false),
    new BracePair(mREGEX_BEGIN, mREGEX_END, false),
    new BracePair(mDOLLAR_SLASH_REGEX_BEGIN, mDOLLAR_SLASH_REGEX_END, false),
  };

  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@Nonnull IElementType braceType, @Nullable IElementType tokenType) {
    return tokenType == null
      || tokenType == WHITE_SPACE
      || tokenType == mSEMI
      || tokenType == mCOMMA
      || tokenType == mRPAREN
      || tokenType == mRBRACK
      || tokenType == mRCURLY
      || tokenType == mGSTRING_BEGIN
      || tokenType == mREGEX_BEGIN
      || tokenType == mDOLLAR_SLASH_REGEX_BEGIN
      || COMMENT_SET.contains(tokenType)
      || tokenType.getLanguage() != GROOVY_LANGUAGE;
  }

  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}