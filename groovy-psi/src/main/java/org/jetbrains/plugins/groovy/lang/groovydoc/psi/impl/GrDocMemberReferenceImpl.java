/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

/**
 * @author ilyas
 */
public abstract class GrDocMemberReferenceImpl extends GroovyDocPsiElementImpl implements GrDocMemberReference {
  public GrDocMemberReferenceImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  @RequiredReadAction
  public GrDocReferenceElement getReferenceHolder() {
    return findChildByClass(GrDocReferenceElement.class);
  }

  @Override
  @RequiredReadAction
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
  }

  @Nullable
  @Override
  @RequiredWriteAction
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;

    if (element instanceof PsiClass) {
      GrDocReferenceElement holder = getReferenceHolder();
      if (holder != null) {
        return replace(holder.getReferenceElement().bindToElement(element).getParent());
      }
      GrDocReferenceElement ref =
        GroovyPsiElementFactory.getInstance(getProject()).createDocReferenceElementFromFQN(((PsiClass)element).getQualifiedName());
      return replace(ref);
    }
    else if (element instanceof PsiMember member) {
      PsiClass clazz = member.getContainingClass();
      if (clazz == null) return null;
      String qName = clazz.getQualifiedName();
      String memberRefText;
      if (element instanceof PsiField field) {
        memberRefText = field.getName();
      }
      else if (element instanceof PsiMethod method) {
        PsiParameterList list = method.getParameterList();
        StringBuilder builder = new StringBuilder();
        builder.append(method.getName()).append("(");
        PsiParameter[] params = list.getParameters();
        for (int i = 0; i < params.length; i++) {
          PsiParameter parameter = params[i];
          PsiType type = parameter.getType();
          if (i > 0) builder.append(", ");
          builder.append(type.getPresentableText());
        }
        builder.append(")");
        memberRefText = builder.toString();
      }
      else {
        return null;
      }
      GrDocMemberReference ref = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceFromText(qName, memberRefText);
      return replace(ref);
    }
    return null;
  }

  @Override
  @RequiredWriteAction
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException
  {
    PsiElement nameElement = getReferenceNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);
    return this;
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GrDocTagValueToken getReferenceNameElement() {
    GrDocTagValueToken token = findChildByClass(GrDocTagValueToken.class);
    assert token != null;
    return token;
  }

  @Override
  @RequiredReadAction
  public PsiElement getElement() {
    return this;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  @RequiredReadAction
  public TextRange getRangeInElement() {
    PsiElement refNameElement = getReferenceNameElement();
    int offsetInParent = refNameElement.getStartOffsetInParent();
    return new TextRange(offsetInParent, offsetInParent + refNameElement.getTextLength());
  }

  @Override
  @RequiredReadAction
  @Nonnull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  @RequiredReadAction
  public boolean isSoft() {
    return false;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PsiElement getQualifier() {
    return getReferenceHolder();
  }

  @Nullable
  @Override
  @RequiredReadAction
  public String getReferenceName() {
    return getReferenceNameElement().getText();
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    if (results.length == 1) {
      return results[0].getElement();
    }
    return null;
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return multiResolveImpl();
  }

  protected abstract ResolveResult[] multiResolveImpl();
}
