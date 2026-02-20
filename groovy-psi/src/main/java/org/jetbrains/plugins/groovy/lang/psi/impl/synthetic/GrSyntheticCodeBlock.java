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

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiStatement;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SoftReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

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
    PsiElement nextSibling = myCodeBlock.getLBrace().getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  @Override
  public PsiElement getLastBodyElement() {
    PsiElement rBrace = myCodeBlock.getRBrace();
    if (rBrace != null) {
      PsiElement prevSibling = rBrace.getPrevSibling();
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

  @Nullable
  private static PsiJavaToken getOrCreateJavaToken(@Nullable PsiElement element, @Nonnull IElementType type) {
    if (element == null) return null;

    SoftReference<PsiJavaToken> ref = element.getUserData(PSI_JAVA_TOKEN);
    PsiJavaToken token = SoftReference.dereference(ref);
    if (token != null) return token;
    LightJavaToken newToken = new LightJavaToken(element, type);
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
  public void delete() throws IncorrectOperationException
  {
    myCodeBlock.delete();
  }
}
