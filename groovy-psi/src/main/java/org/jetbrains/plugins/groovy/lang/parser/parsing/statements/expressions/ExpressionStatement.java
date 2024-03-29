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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import consulo.language.ast.IElementType;
import consulo.language.parser.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.CommandArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.UnaryExpressionNotPlusMinus;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Main classdef for any general expression parsing
 *
 * http://svn.codehaus.org/groovy/trunk/groovy/groovy-core/src/test/gls/syntax/Gep3Test.groovy
 * @author ilyas
 */
public class ExpressionStatement {

  private static IElementType parseExpressionStatement(PsiBuilder builder, GroovyParser parser) {
    if (checkForTypeCast(builder, parser)) return GroovyElementTypes.CAST_EXPRESSION;
    PsiBuilder.Marker marker = builder.mark();
    final PathExpression.Result result = PathExpression.parsePathExprQualifierForExprStatement(builder, parser);
    if (result != PathExpression.Result.WRONG_WAY &&
        !TokenSets.SEPARATORS.contains(builder.getTokenType()) &&
        !TokenSets.BINARY_OP_SET.contains(builder.getTokenType()) &&
        !TokenSets.POSTFIX_UNARY_OP_SET.contains(builder.getTokenType())) {
      if (result == PathExpression.Result.METHOD_CALL) {
        marker.drop();
        return GroovyElementTypes.PATH_METHOD_CALL;
      }

      if (result == PathExpression.Result.LITERAL) {
        final PsiBuilder.Marker newMarker = marker.precede();
        marker.rollbackTo();
        marker = newMarker;
        PrimaryExpression.parsePrimaryExpression(builder, parser, true);
      }
      if (CommandArguments.parseCommandArguments(builder, parser)) {
        marker.done(GroovyElementTypes.CALL_EXPRESSION);
        return GroovyElementTypes.CALL_EXPRESSION;
      }
    }
    marker.drop();
    return GroovyElementTypes.WRONGWAY;
  }

  private static boolean checkForTypeCast(PsiBuilder builder, GroovyParser parser) {
    return UnaryExpressionNotPlusMinus.parse(builder, parser, false);
  }

  /**
   * Use for parse expressions in Argument position
   *
   * @param builder - Given builder
   * @return type of parsing result
   */
  public static boolean argParse(PsiBuilder builder, GroovyParser parser) {
    return AssignmentExpression.parse(builder, parser);
  }

  enum Result {
    WRONG_WAY, EXPR_STATEMENT, EXPRESSION
  }

  public static Result parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();

    final IElementType result = parseExpressionStatement(builder, parser);
    if (result != GroovyElementTypes.CALL_EXPRESSION && result != GroovyElementTypes.PATH_METHOD_CALL) {
      marker.drop();
      return result == GroovyElementTypes.WRONGWAY ? Result.WRONG_WAY : Result.EXPRESSION;
    }

    boolean isExprStatement = result == GroovyElementTypes.CALL_EXPRESSION;

    while (true) {
      boolean nameParsed = namePartParse(builder, parser) == GroovyElementTypes.REFERENCE_EXPRESSION;

      PsiBuilder.Marker exprStatement;

      if (nameParsed) {
        exprStatement = marker.precede();
        marker.done(GroovyElementTypes.REFERENCE_EXPRESSION);
      }
      else {
        exprStatement = marker;
      }

      if (builder.getTokenType() == GroovyTokenTypes.mLPAREN) {
        PrimaryExpression.methodCallArgsParse(builder, parser);
        exprStatement.done(GroovyElementTypes.PATH_METHOD_CALL);
      }
      else if (GroovyTokenTypes.mLBRACK.equals(builder.getTokenType()) &&
               !ParserUtils.lookAhead(builder, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mCOLON) &&
               !ParserUtils.lookAhead(builder, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mNLS, GroovyTokenTypes.mCOLON)) {
        PathExpression.indexPropertyArgsParse(builder, parser);
        exprStatement.done(GroovyElementTypes.PATH_INDEX_PROPERTY);
        if (GroovyTokenTypes.mLPAREN.equals(builder.getTokenType())) {
          PrimaryExpression.methodCallArgsParse(builder, parser);
        }
        else if (GroovyTokenTypes.mLCURLY.equals(builder.getTokenType())) {
          PsiBuilder.Marker argsMarker = builder.mark();
          argsMarker.done(GroovyElementTypes.ARGUMENTS);
        }
        while (GroovyTokenTypes.mLCURLY.equals(builder.getTokenType())) {
          OpenOrClosableBlock.parseClosableBlock(builder, parser);
        }
        exprStatement = exprStatement.precede();
        exprStatement.done(GroovyElementTypes.PATH_METHOD_CALL);
      }
      else if (nameParsed && CommandArguments.parseCommandArguments(builder, parser)) {
        isExprStatement = true;
        exprStatement.done(GroovyElementTypes.CALL_EXPRESSION);
      }
      else {
        exprStatement.drop();
        break;
      }

      marker = exprStatement.precede();
    }

    return isExprStatement ? Result.EXPR_STATEMENT : Result.EXPRESSION;
  }

  private static GroovyElementType namePartParse(PsiBuilder builder, GroovyParser parser) {
    if (TokenSets.BINARY_OP_SET.contains(builder.getTokenType())) return GroovyElementTypes.WRONGWAY;
    if (TokenSets.KEYWORDS.contains(builder.getTokenType())) return GroovyElementTypes.WRONGWAY;
    final GroovyElementType type = PathExpression.namePartParse(builder, parser);
    if (type == GroovyElementTypes.WRONGWAY && TokenSets.NUMBERS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return GroovyElementTypes.REFERENCE_EXPRESSION;
    }
    return type;
  }
}
