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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import consulo.language.parser.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstant {
  private static boolean parseEnumConstant(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker ecMarker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    Annotation.parseAnnotationOptional(builder, parser);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
      ecMarker.rollbackTo();
      return false;
    }

    if (GroovyTokenTypes.mLPAREN.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN);
      ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRPAREN, parser);

      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(GroovyElementTypes.ARGUMENTS);
    }

    if (builder.getTokenType() == GroovyTokenTypes.mLCURLY) {
      final PsiBuilder.Marker enumInitializer = builder.mark();
      TypeDefinition.parseBody(builder, null, parser, false);
      enumInitializer.done(GroovyElementTypes.ENUM_CONSTANT_INITIALIZER);
    }

    ecMarker.done(GroovyElementTypes.ENUM_CONSTANT);
    return true;

  }

  public static boolean parseConstantList(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker enumConstantsMarker = builder.mark();

    if (!parseEnumConstant(builder, parser)) {
      enumConstantsMarker.drop();
      return false;
    }

    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA) ||
           ParserUtils.getToken(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mCOMMA)) {
      parseEnumConstant(builder, parser);
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA);

    enumConstantsMarker.done(GroovyElementTypes.ENUM_CONSTANTS);

    return true;
  }
}
