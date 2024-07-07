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

package org.jetbrains.plugins.groovy.lang.parser;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.*;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.IStubFileElementType;
import consulo.language.util.LanguageUtil;
import consulo.language.version.LanguageVersion;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrStubFileElementType;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyParserDefinition implements ParserDefinition {
  public static final IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyLanguage.INSTANCE);
  private static final IElementType[] STRINGS = new IElementType[]{
    GSTRING,
    REGEX,
    GSTRING_INJECTION,
    GroovyTokenTypes.mREGEX_LITERAL,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL
  };

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  @Override
  @Nonnull
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new GroovyLexer();
  }

  @Override
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new GroovyParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return GROOVY_FILE;
  }

  @Override
  @Nonnull
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return TokenSets.WHITE_SPACE_TOKEN_SET;
  }

  @Override
  @Nonnull
  public TokenSet getCommentTokens(LanguageVersion languageVersion) {
    return TokenSets.COMMENTS_TOKEN_SET;
  }

  @Override
  @Nonnull
  public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
    return TokenSets.STRING_LITERALS;
  }

  @Override
  @Nonnull
  public PsiElement createElement(ASTNode node) {
    return GroovyPsiCreator.createElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new GroovyFileImpl(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final IElementType lType = left.getElementType();
    final IElementType rType = right.getElementType();

    if (rType == GroovyTokenTypes.kIMPORT && lType != TokenType.WHITE_SPACE) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    else if (lType == MODIFIERS && rType == MODIFIERS) {
      return SpaceRequirements.MUST;
    }
    if (lType == GroovyTokenTypes.mSEMI || lType == GroovyTokenTypes.mSL_COMMENT) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    if (lType == GroovyTokenTypes.mNLS || lType == GroovyDocTokenTypes.mGDOC_COMMENT_START) {
      return SpaceRequirements.MAY;
    }
    if (lType == GroovyTokenTypes.mGT) {
      return SpaceRequirements.MUST;
    }
    if (rType == GroovyTokenTypes.mLT) {
      return SpaceRequirements.MUST;
    }

    final ASTNode parent = TreeUtil.findCommonParent(left, right);
    if (parent == null || ArrayUtil.contains(parent.getElementType(), STRINGS)) {
      return SpaceRequirements.MUST_NOT;
    }

    return LanguageUtil.canStickTokensTogetherByLexer(left, right, new GroovyLexer());
  }
}
