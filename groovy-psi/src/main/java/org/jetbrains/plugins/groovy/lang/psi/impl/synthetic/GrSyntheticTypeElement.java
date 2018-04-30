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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Max Medvedev
 */
public class GrSyntheticTypeElement extends LightElement implements PsiTypeElement {
  @Nonnull
  private final GrTypeElement myElement;

  public GrSyntheticTypeElement(@Nonnull GrTypeElement element) {
    super(element.getManager(), element.getLanguage());

    myElement = element;
  }

  @Nonnull
  @Override
  public PsiType getType() {
    return myElement.getType();
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  @Nonnull
  @Override
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiAnnotation[] getApplicableAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName) {
    return null;
  }

  @Nonnull
  @Override
  public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "Synthetic PsiTypeElement";
  }

  @Override
  public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException {
    if (newElement instanceof PsiTypeElement) {
      GrTypeElement groovyTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(((PsiTypeElement)newElement).getType());
      return myElement.replace(groovyTypeElement);
    }
    else {
      return super.replace(newElement);
    }
  }

  @Override
  public TextRange getTextRange() {
    return myElement.getTextRange();
  }

  @Override
  public int getTextOffset() {
    return myElement.getTextOffset();
  }

  @Override
  public String getText() {
    return myElement.getText();
  }

  @Override
  public boolean isValid() {
    return myElement.isValid();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
  }

  @Override
  public void acceptChildren(@Nonnull PsiElementVisitor visitor) {
    final PsiElement[] children = myElement.getChildren();
    for (PsiElement child : children) {
      if (child instanceof GrTypeElement) {
        PsiImplUtil.getOrCreateTypeElement((GrTypeElement)child).accept(visitor);
      }
    }
  }
}
