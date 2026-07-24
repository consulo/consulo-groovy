/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocParameterReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrDocParameterReferenceImpl extends GroovyDocPsiElementImpl implements GrDocParameterReference {

  public GrDocParameterReferenceImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "GrDocParameterReference";
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    String name = getName();
    if (name == null) return ResolveResult.EMPTY_ARRAY;
    List<GroovyResolveResult> candidates = new ArrayList<>();

    PsiElement owner = GrDocCommentUtil.findDocOwner(this);
    if (owner instanceof GrMethod method) {
      GrParameter[] parameters = method.getParameters();

      for (GrParameter parameter : parameters) {
        if (name.equals(parameter.getName())) {
          candidates.add(new GroovyResolveResultImpl(parameter, true));
        }
      }
      return candidates.toArray(new ResolveResult[candidates.size()]);
    }
    else {
      PsiElement firstChild = getFirstChild();
      if (owner instanceof GrTypeParameterListOwner typeParamListOwner && firstChild != null) {
        ASTNode node = firstChild.getNode();
        if (node != null && GroovyDocTokenTypes.mGDOC_TAG_VALUE_LT.equals(node.getElementType())) {
          PsiTypeParameter[] typeParameters = typeParamListOwner.getTypeParameters();
          for (PsiTypeParameter typeParameter : typeParameters) {
            if (name.equals(typeParameter.getName())) {
              candidates.add(new GroovyResolveResultImpl(typeParameter, true));
            }
          }
        }
      }
    }
    return ResolveResult.EMPTY_ARRAY;
  }

  @Override
  @RequiredReadAction
  public PsiElement getElement() {
    return this;
  }

  @Override
  @RequiredReadAction
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  @RequiredReadAction
  public String getName() {
    return getText();
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    if (results.length != 1) return null;
    return results[0].getElement();
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public String getCanonicalText() {
    return getName();
  }

  @Override
  @RequiredWriteAction
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode node = getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null;
    node.getTreeParent().replaceChild(node, newNameNode);
    return this;
  }

  @Override
  @RequiredWriteAction
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;
    return null;
  }

  @Override
  @RequiredReadAction
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof GrParameter || element instanceof GrTypeParameter)) return false;
    return getManager().areElementsEquivalent(element, resolve());
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public Object[] getVariants() {
    PsiElement owner = GrDocCommentUtil.findDocOwner(this);
    PsiElement firstChild = getFirstChild();
    if (owner instanceof GrTypeParameterListOwner typeParamListOwner && firstChild != null) {
      ASTNode node = firstChild.getNode();
      if (node != null && GroovyDocTokenTypes.mGDOC_TAG_VALUE_LT.equals(node.getElementType())) {
        return typeParamListOwner.getTypeParameters();
      }
    }
    if (owner instanceof PsiMethod method) {
      return method.getParameterList().getParameters();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GrDocTagValueToken getReferenceNameElement() {
    GrDocTagValueToken token = findChildByClass(GrDocTagValueToken.class);
    assert token != null;
    return token;
  }
}
