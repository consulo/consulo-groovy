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
package org.jetbrains.plugins.groovy.impl.formatter.blocks;

import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.*;
import consulo.language.codeStyle.Wrap;

import jakarta.annotation.Nonnull;

import consulo.language.codeStyle.Block;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.formatter.FormattingContext;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class ClosureBodyBlock extends GroovyBlock {
  private TextRange myTextRange;

  public ClosureBodyBlock(@Nonnull ASTNode node,
                          @Nonnull Indent indent,
                          @Nullable Wrap wrap,
                          FormattingContext context) {
    super(node, indent, wrap, context);
  }

  @Nonnull
  @Override
  public TextRange getTextRange() {
    init();
    return myTextRange;
  }

  private void init() {
    if (mySubBlocks == null) {
      GroovyBlockGenerator generator = new GroovyBlockGenerator(this);
      List<ASTNode> children = GroovyBlockGenerator.getClosureBodyVisibleChildren(myNode.getTreeParent());

      mySubBlocks = generator.generateSubBlockForCodeBlocks(false, children, myContext.getGroovySettings().INDENT_LABEL_BLOCKS);

      //at least -> exists
      assert !mySubBlocks.isEmpty();
      TextRange firstRange = mySubBlocks.get(0).getTextRange();
      TextRange lastRange = mySubBlocks.get(mySubBlocks.size() - 1).getTextRange();
      myTextRange = new TextRange(firstRange.getStartOffset(), lastRange.getEndOffset());
    }
  }

  @Nonnull
  @Override
  public List<Block> getSubBlocks() {
    init();
    return mySubBlocks;
  }

  @Nonnull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNormalIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return true;
  }
}
