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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import com.intellij.openapi.diagnostic.Logger;
import consulo.util.dataholder.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Medvedev Max
 */
public class GrSyntheticCodeBlock extends LightElement implements PsiCodeBlock {
  private static final Logger LOG = Logger.getInstance(GrSyntheticCodeBlock.class);
  private final GrCodeBlock myCodeBlock;
  private static final Key<SoftReference<PsiJavaToken>> PSI_JAVA_TOKEN = Key.create("psi_java_token");

  public GrSyntheticCodeBlock(@Nonnull GrCodeBlock codeBlock) {
    super(codeBlock.getManager(), codeBlock.getLanguage());
    myCodeBlock = codeBlock;
  }

  @Override
  public String toString() {
    return "code block wrapper to represent java codeBlock";
  }

  @Nonnull
  @Override
  public PsiStatement[] getStatements() {
    return PsiStatement.EMPTY_ARRAY; //todo return statements
  }

  @Override
  public PsiElement getFirstBodyElement() {
    final PsiElement nextSibling = myCodeBlock.getLBrace().getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  @Override
  public PsiElement getLastBodyElement() {
    final PsiElement rBrace = myCodeBlock.getRBrace();
    if (rBrace != null) {
      final PsiElement prevSibling = rBrace.getPrevSibling();
      return prevSibling == myCodeBlock.getLBrace() ? null : prevSibling;
    }
    return getLastChild();
  }

  @Override
  public PsiJavaToken getLBrace() {
    return getOrCreateJavaToken(myCodeBlock.getLBrace(), JavaTokenType.LBRACE);
  }

  @Override
  public PsiJavaToken getRBrace() {
    return getOrCreateJavaToken(myCodeBlock.getRBrace(), JavaTokenType.RBRACE);
  }

  @javax.annotation.Nullable
  private static PsiJavaToken getOrCreateJavaToken(@javax.annotation.Nullable PsiElement element, @Nonnull IElementType type) {
    if (element == null) return null;

    final SoftReference<PsiJavaToken> ref = element.getUserData(PSI_JAVA_TOKEN);
    final PsiJavaToken token = SoftReference.dereference(ref);
    if (token != null) return token;
    final LightJavaToken newToken = new LightJavaToken(element, type);
    element.putUserData(PSI_JAVA_TOKEN, new SoftReference<PsiJavaToken>(newToken));
    return newToken;
  }

  @Override
  public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException {
    if (newElement instanceof GrSyntheticCodeBlock) {
      GrSyntheticCodeBlock other = (GrSyntheticCodeBlock)newElement;
      PsiElement replaced = myCodeBlock.replace(other.myCodeBlock);
      LOG.assertTrue(replaced instanceof GrOpenBlock);
      return PsiImplUtil.getOrCreatePsiCodeBlock((GrOpenBlock)replaced);
    }
    return super.replace(newElement);
  }

  @Override
  public boolean shouldChangeModificationCount(PsiElement place) {
    return false;
  }

  @Override
  public TextRange getTextRange() {
    return myCodeBlock.getTextRange();
  }

  @Override
  public int getStartOffsetInParent() {
    return myCodeBlock.getStartOffsetInParent();
  }

  @Override
  public PsiFile getContainingFile() {
    return myCodeBlock.getContainingFile();
  }

  @Override
  public int getTextOffset() {
    return myCodeBlock.getTextOffset();
  }

  @Override
  public String getText() {
    return myCodeBlock.getText();
  }

  @Nonnull
  @Override
  public PsiElement getNavigationElement() {
    return myCodeBlock;
  }

  @Override
  public boolean isValid() {
    return myCodeBlock.isValid();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    myCodeBlock.delete();
  }
}
