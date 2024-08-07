/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.language.Language;
import consulo.language.ast.*;
import consulo.language.lexer.Lexer;
import consulo.project.Project;
import consulo.language.ast.ICompositeElementType;
import consulo.language.ast.IErrorCounterReparseableElementType;

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;

/**
 * @author peter
 */
public abstract class GrCodeBlockElementType extends IErrorCounterReparseableElementType implements ICompositeElementType
{

  protected GrCodeBlockElementType(String debugName) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return createNode(null);
  }

  @Override
  @Nonnull
  public abstract GrBlockImpl createNode(final CharSequence text);

  @Override
  public int getErrorsCount(final CharSequence seq, Language fileLanguage, final Project project) {
    final Lexer lexer = new GroovyLexer();

    lexer.start(seq);
    if (lexer.getTokenType() != GroovyTokenTypes.mLCURLY) return FATAL_ERROR;
    lexer.advance();
    int balance = 1;
    while (true) {
      IElementType type = lexer.getTokenType();
      if (type == null) break;
      if (balance == 0) return FATAL_ERROR;
      if (type == GroovyTokenTypes.mLCURLY) {
        balance++;
      }
      else if (type == GroovyTokenTypes.mRCURLY) {
        balance--;
      }
      lexer.advance();
    }
    return balance;
  }
}
