// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import consulo.language.ast.TokenSet;
import consulo.language.ast.TokenType;
import consulo.language.lexer.FlexAdapter;
import consulo.language.lexer.LookAheadLexer;
import consulo.language.lexer.MergingLexerAdapter;

public class GroovyDocLexer extends LookAheadLexer {

  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
    GroovyDocTokenTypes.mGDOC_COMMENT_DATA,
    GroovyDocTokenTypes.mGDOC_ASTERISKS,
    TokenType.WHITE_SPACE
  );

  public GroovyDocLexer() {
    super(new MergingLexerAdapter(new FlexAdapter(new _GroovyDocLexer(null)), TOKENS_TO_MERGE));
  }
}

