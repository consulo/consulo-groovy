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
package org.jetbrains.plugins.groovy.formatter.blocks;

import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.ast.ASTNode;
import consulo.document.util.TextRange;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.Wrap;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrLabelBlock extends GroovyBlock {
  private List<Block> myBlocks;
  private TextRange myRange;

  public GrLabelBlock(@Nonnull ASTNode node,
                      List<ASTNode> subStatements,
                      boolean classLevel,
                      @Nonnull Indent indent,
                      @Nullable Wrap wrap,
                      @Nonnull FormattingContext context) {
    super(node, indent, wrap, context);

    final GroovyBlockGenerator
      generator = new GroovyBlockGenerator(this);
    myBlocks = generator.generateSubBlockForCodeBlocks(classLevel, subStatements, false);
    myRange = new TextRange(subStatements.get(0).getTextRange().getStartOffset(),
                            subStatements.get(subStatements.size() - 1).getTextRange().getEndOffset());
  }

  @Nonnull
  @Override
  public TextRange getTextRange() {
    return myRange;
  }

  @Nonnull
  @Override
  public List<Block> getSubBlocks() {
    return myBlocks;
  }
}
