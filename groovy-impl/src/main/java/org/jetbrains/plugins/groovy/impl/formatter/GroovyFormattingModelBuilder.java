/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.formatter;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.*;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.impl.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import jakarta.annotation.Nonnull;


/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyFormattingModelBuilder implements FormattingModelBuilder {
  @Nonnull
  @Override
  public FormattingModel createModel(@Nonnull consulo.language.codeStyle.FormattingContext context) {
    PsiElement element = context.getPsiElement();
    CodeStyleSettings settings = context.getCodeStyleSettings();
    ASTNode node = element.getNode();
    assert node != null;
    PsiFile containingFile = element.getContainingFile().getViewProvider().getPsi(GroovyFileType.GROOVY_LANGUAGE);
    assert containingFile != null : element.getContainingFile();
    ASTNode astNode = containingFile.getNode();
    assert astNode != null;
    CommonCodeStyleSettings groovySettings = settings.getCommonSettings(GroovyFileType.GROOVY_LANGUAGE);
    GroovyCodeStyleSettings customSettings = settings.getCustomSettings(GroovyCodeStyleSettings.class);

    final AlignmentProvider alignments = new AlignmentProvider();
    if (customSettings.USE_FLYING_GEESE_BRACES) {
      element.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (GeeseUtil.isClosureRBrace(element)) {
            GeeseUtil.calculateRBraceAlignment(element, alignments);
          }
          else {
            super.visitElement(element);
          }
        }
      });
    }
    GroovyBlock block = new GroovyBlock(astNode,
                                              Indent.getAbsoluteNoneIndent(),
                                              null,
                                              new FormattingContext(groovySettings, alignments, customSettings, false));
    return new GroovyFormattingModel(containingFile,
                                     block,
                                     FormattingDocumentModel.create(containingFile));

  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  /**
   * Standard {@link PsiBasedFormattingModel} extension that handles the fact that groovy uses not single white space token type
   * ({@link TokenType#WHITE_SPACE}) but one additional token type as well: {@link GroovyTokenTypes#mNLS}. So, it allows to adjust
   * white space token type to use for calling existing common formatting stuff.
   */
  private static class GroovyFormattingModel extends PsiBasedFormattingModel {

    GroovyFormattingModel(PsiFile file, @Nonnull Block rootBlock, FormattingDocumentModel documentModel) {
      super(file, rootBlock, documentModel);
    }

    @Override
    protected String replaceWithPsiInLeaf(TextRange textRange, String whiteSpace, ASTNode leafElement) {
      if (!myCanModifyAllWhiteSpaces) {
        if (TokenSets.WHITE_SPACES_SET.contains(leafElement.getElementType())) return null;
      }

      IElementType elementTypeToUse = TokenType.WHITE_SPACE;
      ASTNode prevNode = TreeUtil.prevLeaf(leafElement);
      if (prevNode != null && TokenSets.WHITE_SPACES_SET.contains(prevNode.getElementType())) {
        elementTypeToUse = prevNode.getElementType();
      }
      FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, elementTypeToUse, textRange);
      return whiteSpace;
    }
  }
}
