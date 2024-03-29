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
package org.jetbrains.plugins.groovy.lang.lexer;

import static consulo.language.psi.search.UsageSearchContext.IN_CODE;
import static consulo.language.psi.search.UsageSearchContext.IN_COMMENTS;
import static consulo.language.psi.search.UsageSearchContext.IN_FOREIGN_LANGUAGES;
import static consulo.language.psi.search.UsageSearchContext.IN_PLAIN_TEXT;
import static consulo.language.psi.search.UsageSearchContext.IN_STRINGS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIDENT;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.COMMENT_SET;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.STRING_LITERALS;

import consulo.language.lexer.Lexer;
import consulo.language.psi.stub.BaseFilterLexer;
import consulo.language.psi.stub.OccurrenceConsumer;
import consulo.language.ast.IElementType;

/**
 * @author Maxim.Medvedev
 */
public class GroovyFilterLexer extends BaseFilterLexer {

  public GroovyFilterLexer(Lexer originalLexer, OccurrenceConsumer occurrenceConsumer) {
    super(originalLexer, occurrenceConsumer);
  }


  public void advance() {
    final IElementType tokenType = getDelegate().getTokenType();

    if (tokenType == mIDENT || TokenSets.KEYWORDS.contains(tokenType)) {
      addOccurrenceInToken(IN_CODE);
    }
    else if (STRING_LITERALS.contains(tokenType)) {
      scanWordsInToken(IN_STRINGS | IN_FOREIGN_LANGUAGES, false, true);
    }
    else if (COMMENT_SET.contains(tokenType)) {
      scanWordsInToken(IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }
    else {
      scanWordsInToken(IN_PLAIN_TEXT, false, false);
    }

    getDelegate().advance();
  }
}
