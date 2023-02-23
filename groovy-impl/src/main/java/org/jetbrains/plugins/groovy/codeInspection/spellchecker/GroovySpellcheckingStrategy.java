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
package org.jetbrains.plugins.groovy.codeInspection.spellchecker;

import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.spellcheker.SpellcheckingStrategy;
import consulo.language.spellcheker.tokenizer.EscapeSequenceTokenizer;
import consulo.language.spellcheker.tokenizer.TokenConsumer;
import consulo.language.spellcheker.tokenizer.Tokenizer;
import consulo.language.spellcheker.tokenizer.splitter.PlainTextTokenSplitter;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import javax.annotation.Nonnull;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovySpellcheckingStrategy extends SpellcheckingStrategy {
  private final GrDocCommentTokenizer myDocCommentTokenizer = new GrDocCommentTokenizer();
  private final Tokenizer<PsiElement> myStringTokenizer = new Tokenizer<PsiElement>() {
    @Override
    public void tokenize(@Nonnull PsiElement literal, TokenConsumer consumer) {
      String text = GrStringUtil.removeQuotes(literal.getText());
      if (!text.contains("\\")) {
        consumer.consumeToken(literal, PlainTextTokenSplitter.getInstance());
      }
      else {
        StringBuilder unescapedText = new StringBuilder();
        int[] offsets = new int[text.length() + 1];
        GrStringUtil.parseStringCharacters(text, unescapedText, offsets);
        EscapeSequenceTokenizer.processTextWithOffsets(literal, consumer, unescapedText, offsets, GrStringUtil.getStartQuote(literal.getText
          ()).length());
      }
    }
  };

  @Nonnull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (TokenSets.STRING_LITERAL_SET.contains(element.getNode().getElementType())) {
      return myStringTokenizer;
    }
    if (element instanceof GrNamedElement) {
      final PsiElement name = ((GrNamedElement)element).getNameIdentifierGroovy();
      if (TokenSets.STRING_LITERAL_SET.contains(name.getNode().getElementType())) {
        return EMPTY_TOKENIZER;
      }
    }
    if (element instanceof PsiDocComment) {
      return myDocCommentTokenizer;
    }
    //if (element instanceof GrLiteralImpl && ((GrLiteralImpl)element).isStringLiteral()) return myStringTokenizer;
    return super.getTokenizer(element);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
