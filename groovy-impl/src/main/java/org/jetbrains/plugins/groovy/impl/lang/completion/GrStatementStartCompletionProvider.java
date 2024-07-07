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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.impl.codeInsight.TailTypes;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;

import jakarta.annotation.Nonnull;

/**
* Created by Max Medvedev on 14/05/14
*/
class GrStatementStartCompletionProvider implements CompletionProvider
{
  private static final PsiElementPattern.Capture<PsiElement> STATEMENT_START =
    PlatformPatterns.psiElement(GroovyTokenTypes.mIDENT).andOr(
      PlatformPatterns.psiElement().afterLeaf(StandardPatterns.or(
        PlatformPatterns.psiElement().isNull(),
        PlatformPatterns.psiElement().withElementType(TokenSets.SEPARATORS),
        PlatformPatterns.psiElement(GroovyTokenTypes.mLCURLY),
        PlatformPatterns.psiElement(GroovyTokenTypes.kELSE)
      )).andNot(PlatformPatterns.psiElement().withParent(GrTypeDefinitionBody.class))
        .andNot(PlatformPatterns.psiElement(PsiErrorElement.class)),
      PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(GroovyTokenTypes.mRPAREN)).withSuperParent(2, StandardPatterns.or(
        PlatformPatterns.psiElement(GrForStatement.class),
        PlatformPatterns.psiElement(GrWhileStatement.class),
        PlatformPatterns.psiElement(GrIfStatement.class)
      ))
    );

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, STATEMENT_START, new GrStatementStartCompletionProvider());
  }

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters,
                                ProcessingContext context,
                                @Nonnull CompletionResultSet result) {
    result.addElement(LookupElementBuilder.create("if").bold().withInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        if (context.getCompletionChar() != ' ') {
          TailTypes.IF_LPARENTH.processTail(context.getEditor(), context.getTailOffset());
        }
        if (context.getCompletionChar() == '(') {
          context.setAddCompletionChar(false);
        }
      }
    }));
  }
}
