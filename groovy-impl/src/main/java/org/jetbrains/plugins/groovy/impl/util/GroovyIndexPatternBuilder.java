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
package org.jetbrains.plugins.groovy.impl.util;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.lexer.Lexer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.search.IndexPatternBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.07.2008
 */
@ExtensionImpl
public class GroovyIndexPatternBuilder implements IndexPatternBuilder {
  public Lexer getIndexingLexer(PsiFile file) {
    if (file instanceof GroovyFile) {
      return new GroovyLexer();
    }
    return null;
  }

  public TokenSet getCommentTokenSet(PsiFile file) {
    return TokenSets.ALL_COMMENT_TOKENS;
  }

  public int getCommentStartDelta(IElementType tokenType) {
    return 0;
  }

  public int getCommentEndDelta(IElementType tokenType) {
    return tokenType == GroovyTokenTypes.mML_COMMENT ? 2 : 0;
  }
}
