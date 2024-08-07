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
package org.jetbrains.plugins.groovy.impl.formatter;

import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nullable;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRCURLY;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.WHITE_SPACES_SET;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLOSABLE_BLOCK;

/**
 * @author Max Medvedev
 */
public class GeeseUtil {
  private static final Logger LOG = Logger.getInstance(GeeseUtil.class);

  private GeeseUtil() {
  }

  @Nullable
  public static ASTNode getClosureRBraceAtTheEnd(ASTNode node) {
    IElementType elementType = node.getElementType();
    if (elementType == CLOSABLE_BLOCK) {
      PsiElement rBrace = ((GrClosableBlock)node.getPsi()).getRBrace();
      return rBrace != null ? rBrace.getNode() : null;
    }

    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null && WHITE_SPACES_SET.contains(lastChild.getElementType())) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) return null;

    return getClosureRBraceAtTheEnd(lastChild);
  }

  public static boolean isClosureRBrace(PsiElement e) {
    return e != null && e.getNode().getElementType() == mRCURLY &&
           e.getParent() instanceof GrClosableBlock &&
           ((GrClosableBlock)e.getParent()).getRBrace() == e;
  }

  @Nullable
  public static PsiElement getNextNonWhitespaceToken(PsiElement e) {
    PsiElement next = PsiTreeUtil.nextLeaf(e);
    while (next != null && next.getNode().getElementType() == TokenType.WHITE_SPACE) next = PsiTreeUtil.nextLeaf(next);
    return next;
  }

  @Nullable
  public static PsiElement getPreviousNonWhitespaceToken(PsiElement e) {
    PsiElement next = PsiTreeUtil.prevLeaf(e);
    while (next != null && next.getNode().getElementType() == TokenType.WHITE_SPACE) next = PsiTreeUtil.prevLeaf(next);
    return next;
  }

  static void calculateRBraceAlignment(PsiElement rBrace, AlignmentProvider alignments) {
    int leadingBraceCount = 0;
    PsiElement next;

    if (!isClosureContainLF(rBrace)) return;

    for (next = getPreviousNonWhitespaceToken(rBrace);
         isClosureRBrace(next) && isClosureContainLF(next);
         next = getPreviousNonWhitespaceToken(next)) {
      leadingBraceCount++;
    }

    PsiElement cur = rBrace;
    for (next = getNextNonWhitespaceToken(cur); isClosureRBrace(next); next = getNextNonWhitespaceToken(cur)) {
      cur = next;
    }

    for (; leadingBraceCount > 0; leadingBraceCount--) {
      cur = getPreviousNonWhitespaceToken(cur);
    }

    PsiElement parent = cur.getParent();
    LOG.assertTrue(parent instanceof GrClosableBlock);

    //search for start of the line
    cur = parent;
    if (cur.getParent() instanceof GrMethodCall) {
      GrMethodCall call = (GrMethodCall)cur.getParent();
      GrExpression invoked = call.getInvokedExpression();
      if (invoked instanceof GrReferenceExpression && ((GrReferenceExpression)invoked).getReferenceNameElement() != null) {
        cur = ((GrReferenceExpression)invoked).getReferenceNameElement();
      }
      else {
        cur = call;
      }
    }
    cur = PsiTreeUtil.getDeepestFirst(cur);
    while (!PsiUtil.isNewLine(next = PsiTreeUtil.prevLeaf(cur, true))) {
      if (next == null) break;
      if (next.getNode().getElementType() == TokenType.WHITE_SPACE && PsiTreeUtil.prevLeaf(next) == null) {
        break; //if cur is first word in the text, whitespace could be before it
      }
      cur = next;
    }

    int startOffset = cur.getTextRange().getStartOffset();
    int endOffset = rBrace.getTextRange().getStartOffset();

    if (rBrace.getContainingFile().getText().substring(startOffset, endOffset).indexOf('\n') < 0) {
      return;
    }

    while (true) {
      final PsiElement p = cur.getParent();
      if (p != null && p.getTextOffset() == cur.getTextOffset()) {
        cur = p;
      }
      else {
        break;
      }
    }
    alignments.addPair(rBrace, cur, true);
  }

  public static boolean isClosureContainLF(PsiElement rBrace) {
    PsiElement parent = rBrace.getParent();
    return parent.getText().indexOf('\n') >= 0;
  }
}
