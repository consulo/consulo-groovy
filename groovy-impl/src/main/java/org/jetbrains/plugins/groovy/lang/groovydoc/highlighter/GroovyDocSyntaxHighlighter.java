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

package org.jetbrains.plugins.groovy.lang.groovydoc.highlighter;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import consulo.colorScheme.TextAttributesKey;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.lexer.Lexer;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementTypeImpl;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocLexer;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import consulo.language.lexer.Lexer;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;

/**
 * @author ilyas
 */
public class GroovyDocSyntaxHighlighter extends SyntaxHighlighterBase implements GroovyDocTokenTypes {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();
  private static final IElementType mGDOC_COMMENT_CONTENT = new GroovyDocElementTypeImpl("GDOC_COMMENT_CONTENT");

  @Nonnull
  public Lexer getHighlightingLexer() {
    return new GroovyDocHighlightingLexer();
  }

  static final TokenSet tGDOC_COMMENT_TAGS = TokenSet.create(
      mGDOC_TAG_NAME
  );

  static final TokenSet tGDOC_COMMENT_CONTENT = TokenSet.create(
      mGDOC_COMMENT_CONTENT
  );


  static {
    fillMap(ATTRIBUTES, tGDOC_COMMENT_CONTENT, GroovySyntaxHighlighter.DOC_COMMENT_CONTENT);
    fillMap(ATTRIBUTES, tGDOC_COMMENT_TAGS, GroovySyntaxHighlighter.DOC_COMMENT_TAG);
  }


  @Nonnull
  public TextAttributesKey[] getTokenHighlights(IElementType type) {
    return pack(ATTRIBUTES.get(type));
  }

  private static class GroovyDocHighlightingLexer extends GroovyDocLexer {
    public IElementType getTokenType() {
      return super.getTokenType() == mGDOC_TAG_NAME ? mGDOC_TAG_NAME : super.getTokenType();
    }
  }
}
