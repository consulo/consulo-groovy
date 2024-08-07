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

import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.WrapType;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import org.jetbrains.plugins.groovy.impl.formatter.FormattingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class MethodCallWithoutQualifierBlock extends GroovyBlock {
  private final PsiElement myNameElement;
  private final boolean myTopLevel;
  private final List<ASTNode> myChildren;
  private final PsiElement myElem;

  public MethodCallWithoutQualifierBlock(PsiElement nameElement,
                                         Wrap wrap,
                                         boolean topLevel,
                                         List<ASTNode> children,
                                         PsiElement elem, FormattingContext context) {
    super(nameElement.getNode(), Indent.getContinuationWithoutFirstIndent(), wrap, context);
    myNameElement = nameElement;
    myTopLevel = topLevel;
    myChildren = children;
    myElem = elem;
  }

  @Nonnull
  @Override
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = new ArrayList<Block>();
      final Indent indent = Indent.getContinuationWithoutFirstIndent();
      mySubBlocks.add(new GroovyBlock(myNameElement.getNode(), indent, Wrap.createWrap(WrapType.NONE, false), myContext));
      new GroovyBlockGenerator(this).addNestedChildrenSuffix(mySubBlocks, null, myTopLevel, myChildren, myChildren.size());
    }
    return mySubBlocks;
  }

  @Nonnull
  @Override
  public TextRange getTextRange() {
    return new TextRange(myNameElement.getTextRange().getStartOffset(), myElem.getTextRange().getEndOffset());
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}