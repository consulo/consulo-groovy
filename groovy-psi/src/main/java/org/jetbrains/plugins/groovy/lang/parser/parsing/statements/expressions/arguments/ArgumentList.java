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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import consulo.language.ast.TokenSet;
import consulo.language.parser.PsiBuilder;
import consulo.language.ast.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ArgumentList {
  private static final TokenSet CONTROL_KEYWORDS = TokenSet.create(GroovyTokenTypes.kASSERT, GroovyTokenTypes.kBREAK,
                                                                   GroovyTokenTypes.kCASE, GroovyTokenTypes.kCLASS,
                                                                   GroovyTokenTypes.kCONTINUE, GroovyTokenTypes.kDEF,
                                                                   GroovyTokenTypes.kDEFAULT, GroovyTokenTypes.kDO, GroovyTokenTypes.kELSE,
                                                                   GroovyTokenTypes.kENUM, GroovyTokenTypes.kFINAL,
                                                                   GroovyTokenTypes.kFOR, GroovyTokenTypes.kFINALLY, GroovyTokenTypes.kIF,
                                                                   GroovyTokenTypes.kIMPLEMENTS, GroovyTokenTypes.kIMPORT,
                                                                   GroovyTokenTypes.kINTERFACE, GroovyTokenTypes.kNATIVE,
                                                                   GroovyTokenTypes.kPACKAGE, GroovyTokenTypes.kPRIVATE,
                                                                   GroovyTokenTypes.kPROTECTED, GroovyTokenTypes.kPUBLIC,
                                                                   GroovyTokenTypes.kRETURN, GroovyTokenTypes.kSTATIC,
                                                                   GroovyTokenTypes.kSTRICTFP, GroovyTokenTypes.kSWITCH,
                                                                   GroovyTokenTypes.kSYNCHRONIZED,
                                                                   GroovyTokenTypes.kTHROW, GroovyTokenTypes.kTHROWS,
                                                                   GroovyTokenTypes.kTRAIT, GroovyTokenTypes.kTRANSIENT,
                                                                   GroovyTokenTypes.kTRY, GroovyTokenTypes.kVOLATILE,
                                                                   GroovyTokenTypes.kWHILE);


  public static void parseArgumentList(PsiBuilder builder, IElementType closingBrace, GroovyParser parser) {
    boolean hasFirstArg = argumentParse(builder, parser);
    if (!hasFirstArg) {
      if (!closingBrace.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      if (GroovyTokenTypes.mRCURLY.equals(builder.getTokenType())) return;

      if (!GroovyTokenTypes.mCOMMA.equals(builder.getTokenType()) &&
              !closingBrace.equals(builder.getTokenType())) {
        builder.advanceLexer();
      }
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    boolean hasErrors = false;
    while (!builder.eof() && !closingBrace.equals(builder.getTokenType())) {
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA) && hasFirstArg) {
        builder.error("',' or '" + closingBrace + "' expected");
        hasErrors = true;
      }
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (hasErrors && CONTROL_KEYWORDS.contains(builder.getTokenType())) {
        return;
      }
      if (!argumentParse(builder, parser)) {
        if (!closingBrace.equals(builder.getTokenType())) {
          builder.error(GroovyBundle.message("expression.expected"));
          hasErrors = true;
        }
        if (GroovyTokenTypes.mRCURLY.equals(builder.getTokenType())) return;

        if (!GroovyTokenTypes.mCOMMA.equals(builder.getTokenType()) &&
                !closingBrace.equals(builder.getTokenType())) {
          builder.advanceLexer();
        }
      }
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
  }

  /**
   * Parses argument, possible with label
   *
   * @param builder
   * @return
   */
  private static boolean argumentParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker argMarker = builder.mark();

    if (argumentLabelStartCheck(builder, parser)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON, GroovyBundle.message("colon.expected"));
      if (!AssignmentExpression.parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      argMarker.done(GroovyElementTypes.NAMED_ARGUMENT);
      return true;
    }

    if (ParserUtils.getToken(builder, GroovyTokenTypes.mSTAR)) {
      if (AssignmentExpression.parse(builder, parser)) {
        argMarker.done(GroovyElementTypes.SPREAD_ARGUMENT);
      }
      else {
        builder.error(GroovyBundle.message("colon.expected"));
        argMarker.done(GroovyElementTypes.NAMED_ARGUMENT);
      }
      return true;
    }

    argMarker.drop();
    return AssignmentExpression.parse(builder, parser);
  }

  /**
   * Checks for argument label. In case when it is so, a caret will not be restored at
   * initial position
   *
   * @param builder
   * @return
   */
  public static boolean argumentLabelStartCheck(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mSTAR, GroovyTokenTypes.mCOLON)) {
      builder.advanceLexer();
      marker.done(GroovyElementTypes.ARGUMENT_LABEL);
      return true;
    }

    final IElementType type = builder.getTokenType();
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mCOLON) ||
        TokenSets.KEYWORDS.contains(type) ||
        GroovyTokenTypes.mSTRING_LITERAL.equals(type) ||
        GroovyTokenTypes.mGSTRING_LITERAL.equals(type)) {
      builder.advanceLexer();
      if (GroovyTokenTypes.mCOLON.equals(builder.getTokenType())) {
        marker.done(GroovyElementTypes.ARGUMENT_LABEL);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }

    if (GroovyTokenTypes.mGSTRING_BEGIN.equals(type) ||
        GroovyTokenTypes.mREGEX_BEGIN.equals(type) ||
        GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN.equals(type) ||
        TokenSets.NUMBERS.contains(type) ||
        GroovyTokenTypes.mLBRACK.equals(type) ||
        GroovyTokenTypes.mLPAREN.equals(type) ||
        GroovyTokenTypes.mLCURLY.equals(type)) {
      PrimaryExpression.parsePrimaryExpression(builder, parser);
      if (GroovyTokenTypes.mCOLON.equals(builder.getTokenType())) {
        marker.done(GroovyElementTypes.ARGUMENT_LABEL);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }

    marker.drop();
    return false;
  }
}
