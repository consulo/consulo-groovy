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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import jakarta.annotation.Nonnull;

import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.parser.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.CompoundStringExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class PathExpression {

  public enum Result {INVOKED_EXPR, METHOD_CALL, WRONG_WAY, LITERAL}

  private static final TokenSet DOTS = TokenSet.create(GroovyTokenTypes.mSPREAD_DOT, GroovyTokenTypes.mOPTIONAL_DOT,
                                                       GroovyTokenTypes.mMEMBER_POINTER, GroovyTokenTypes.mDOT);
  private static final TokenSet PATH_ELEMENT_START = TokenSet.create(GroovyTokenTypes.mSPREAD_DOT, GroovyTokenTypes.mOPTIONAL_DOT,
                                                                     GroovyTokenTypes.mMEMBER_POINTER, GroovyTokenTypes.mLBRACK,
                                                                     GroovyTokenTypes.mLPAREN, GroovyTokenTypes.mLCURLY,
                                                                     GroovyTokenTypes.mDOT);

  public static boolean parse(@Nonnull PsiBuilder builder, @Nonnull GroovyParser parser) {
    return parsePathExprQualifierForExprStatement(builder, parser) != Result.WRONG_WAY;
  }

  /**
   * parses method calls with parentheses, property index access, etc
   */
  @Nonnull
  public static Result parsePathExprQualifierForExprStatement(@Nonnull PsiBuilder builder, @Nonnull GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    IElementType qualifierType = PrimaryExpression.parsePrimaryExpression(builder, parser);
    if (qualifierType != GroovyElementTypes.WRONGWAY) {
      return parseAfterQualifier(builder, parser, marker, qualifierType);
    }
    else {
      marker.drop();
      return Result.WRONG_WAY;
    }
  }

  @Nonnull
  private static Result parseAfterQualifier(@Nonnull PsiBuilder builder,
                                            @Nonnull GroovyParser parser,
                                            @Nonnull PsiBuilder.Marker marker,
                                            @Nonnull IElementType qualifierType) {
    if (isPathElementStart(builder)) {
      if (isLParenthOrLCurlyAfterLiteral(builder, qualifierType)) {
        marker.rollbackTo();
        PsiBuilder.Marker newMarker = builder.mark();
        IElementType newQualifierType = PrimaryExpression.parsePrimaryExpression(builder, parser, true);
        assert newQualifierType != GroovyElementTypes.WRONGWAY;
        return parseAfterReference(builder, parser, newMarker);
      }
      else {
        return parseAfterReference(builder, parser, marker);
      }
    }
    else {
      marker.drop();
      if (qualifierType == GroovyElementTypes.LITERAL) return Result.LITERAL;
      return Result.INVOKED_EXPR;
    }
  }

  private static boolean isLParenthOrLCurlyAfterLiteral(@Nonnull PsiBuilder builder, @Nonnull IElementType qualifierType) {
    return qualifierType == GroovyElementTypes.LITERAL && (checkForLParenth(builder) || checkForLCurly(builder));
  }

  @Nonnull
  private static Result pathElementParse(@Nonnull PsiBuilder builder,
                                         @Nonnull PsiBuilder.Marker marker,
                                         @Nonnull GroovyParser parser,
                                         @Nonnull Result result) {


    // Property reference
    if (DOTS.contains(builder.getTokenType()) || ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mDOT)) {
      if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mDOT)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      }
      ParserUtils.getToken(builder, DOTS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      TypeArguments.parseTypeArguments(builder, true);
      GroovyElementType res = namePartParse(builder, parser);
      if (res != GroovyElementTypes.WRONGWAY) {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(res);
        return parseAfterReference(builder, parser, newMarker);
      }
      else {
        builder.error(GroovyBundle.message("path.selector.expected"));
        marker.drop();
        return result;
      }
    }
    else if (checkForLParenth(builder)) {
      PrimaryExpression.methodCallArgsParse(builder, parser);
      return parseAfterArguments(builder, marker, parser);
    }
    else if (checkForLCurly(builder)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      appendedBlockParse(builder, parser);
      return parseAfterArguments(builder, marker, parser);
    }
    else if (checkForArrayAccess(builder)) {
      indexPropertyArgsParse(builder, parser);
      PsiBuilder.Marker newMarker = marker.precede();
      marker.done(GroovyElementTypes.PATH_INDEX_PROPERTY);
      return parseAfterReference(builder, parser, newMarker);
    }
    else {
      marker.drop();
      return result;
    }
  }

  @Nonnull
  private static Result parseAfterReference(@Nonnull PsiBuilder builder, @Nonnull GroovyParser parser, @Nonnull PsiBuilder.Marker newMarker) {
    if (checkForLCurly(builder)) {
      PsiBuilder.Marker argsMarker = builder.mark();
      argsMarker.done(GroovyElementTypes.ARGUMENTS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      return pathElementParse(builder, newMarker, parser, Result.METHOD_CALL);
    }
    else {
      return pathElementParse(builder, newMarker, parser, Result.INVOKED_EXPR);
    }
  }

  @Nonnull
  private static Result parseAfterArguments(@Nonnull PsiBuilder builder, @Nonnull PsiBuilder.Marker marker, @Nonnull GroovyParser parser) {
    if (checkForLCurly(builder)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      return pathElementParse(builder, marker, parser, Result.METHOD_CALL);
    }
    else {
      PsiBuilder.Marker newMarker = marker.precede();
      marker.done(GroovyElementTypes.PATH_METHOD_CALL);
      return pathElementParse(builder, newMarker, parser, Result.METHOD_CALL);
    }
  }

  private static boolean checkForLCurly(@Nonnull PsiBuilder builder) {
    return ParserUtils.lookAhead(builder, GroovyTokenTypes.mLCURLY) || ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS,
                                                                                             GroovyTokenTypes.mLCURLY);
  }

  private static boolean checkForLParenth(@Nonnull PsiBuilder builder) {
    return builder.getTokenType() == GroovyTokenTypes.mLPAREN;
  }

  public static boolean checkForArrayAccess(@Nonnull PsiBuilder builder) {
    return builder.getTokenType() == GroovyTokenTypes.mLBRACK &&
           !ParserUtils.lookAhead(builder, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mCOLON) &&
           !ParserUtils.lookAhead(builder, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mNLS, GroovyTokenTypes.mCOLON);
  }

  /**
   * Property selector parsing
   *
   * @param builder
   * @return
   */
  @Nonnull
  public static GroovyElementType namePartParse(@Nonnull PsiBuilder builder, @Nonnull GroovyParser parser) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mAT);
    if (ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT) ||
        ParserUtils.getToken(builder, GroovyTokenTypes.mSTRING_LITERAL) ||
        ParserUtils.getToken(builder, GroovyTokenTypes.mGSTRING_LITERAL)) {
      return GroovyElementTypes.REFERENCE_EXPRESSION;
    }

    final IElementType tokenType = builder.getTokenType();
    if (tokenType == GroovyTokenTypes.mGSTRING_BEGIN) {
      final boolean result = CompoundStringExpression.parse(builder, parser, true, GroovyTokenTypes.mGSTRING_BEGIN,
                                                            GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mGSTRING_END, null,
                                                            GroovyElementTypes.GSTRING, GroovyBundle.message("string.end.expected"));
      return result ? GroovyElementTypes.PATH_PROPERTY_REFERENCE : GroovyElementTypes.REFERENCE_EXPRESSION;
    }
    if (tokenType == GroovyTokenTypes.mREGEX_BEGIN) {
      final boolean result = CompoundStringExpression.parse(builder, parser, true,
                                                            GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_CONTENT,
                                                            GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mREGEX_LITERAL,
                                                            GroovyElementTypes.REGEX, GroovyBundle.message("regex.end.expected"));
      return result ? GroovyElementTypes.PATH_PROPERTY_REFERENCE : GroovyElementTypes.REFERENCE_EXPRESSION;
    }
    if (tokenType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN) {
      final boolean result = CompoundStringExpression.parse(builder, parser, true,
                                                            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
                                                            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
                                                            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END,
                                                            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL,
                                                            GroovyElementTypes.REGEX, GroovyBundle.message("dollar.slash.end.expected"));
      return result ? GroovyElementTypes.PATH_PROPERTY_REFERENCE : GroovyElementTypes.REFERENCE_EXPRESSION;
    }
    if (tokenType == GroovyTokenTypes.mLCURLY) {
      OpenOrClosableBlock.parseOpenBlock(builder, parser);
      return GroovyElementTypes.PATH_PROPERTY_REFERENCE;
    }
    if (tokenType == GroovyTokenTypes.mLPAREN) {
      PrimaryExpression.parenthesizedExprParse(builder, parser);
      return GroovyElementTypes.PATH_PROPERTY_REFERENCE;
    }
    if (TokenSets.KEYWORDS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return GroovyElementTypes.REFERENCE_EXPRESSION;
    }
    return GroovyElementTypes.WRONGWAY;
  }

  /**
   * Method call parsing
   *
   * @param builder
   * @return
   */
  @Nonnull
  public static GroovyElementType indexPropertyArgsParse(@Nonnull PsiBuilder builder, @Nonnull GroovyParser parser) {
    assert builder.getTokenType() == GroovyTokenTypes.mLBRACK;

    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRBRACK, parser);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
    marker.done(GroovyElementTypes.ARGUMENTS);
    return GroovyElementTypes.PATH_INDEX_PROPERTY;
  }

  /**
   * Appended all argument parsing
   *
   * @param builder
   * @return
   */
  @Nonnull
  private static IElementType appendedBlockParse(@Nonnull PsiBuilder builder, @Nonnull GroovyParser parser) {
    return OpenOrClosableBlock.parseClosableBlock(builder, parser);
  }


  /**
   * Checks for path element start
   *
   * @param builder
   * @return
   */
  private static boolean isPathElementStart(@Nonnull PsiBuilder builder) {
    return (PATH_ELEMENT_START.contains(builder.getTokenType()) ||
            ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mDOT) ||
            ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mLCURLY));
  }

}
