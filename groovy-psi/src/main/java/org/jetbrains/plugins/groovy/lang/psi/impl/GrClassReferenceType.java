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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.scope.GlobalSearchScope;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

/**
 * @author ven
 */
public class GrClassReferenceType extends PsiClassType {
  private final GrReferenceElement myReferenceElement;

  public GrClassReferenceType(GrReferenceElement referenceElement) {
    super(LanguageLevel.JDK_1_5);
    myReferenceElement = referenceElement;
  }
  public GrClassReferenceType(GrReferenceElement referenceElement, LanguageLevel languageLevel) {
    super(languageLevel);
    myReferenceElement = referenceElement;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PsiClass resolve() {
    ResolveResult[] results = multiResolve();
    if (results.length == 1) {
      PsiElement only = results[0].getElement();
      return only instanceof PsiClass ? (PsiClass) only : null;
    }

    return null;
  }

  //reference resolve is cached
  @RequiredReadAction
  private GroovyResolveResult[] multiResolve() {
    return myReferenceElement.multiResolve(false);
  }

  @Nullable
  @Override
  @RequiredReadAction
  public String getClassName() {
    PsiClass resolved = resolve();
    if (resolved != null) return resolved.getName();
    return myReferenceElement.getReferenceName();
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public PsiType[] getParameters() {
    return myReferenceElement.getTypeArguments();
  }

  @Nonnull
  @Override
  public ClassResolveResult resolveGenerics() {
    final GroovyResolveResult resolveResult = myReferenceElement.advancedResolve();
    return new ClassResolveResult() {
        @Override
      public PsiClass getElement() {
        PsiElement resolved = resolveResult.getElement();
        return resolved instanceof PsiClass ? (PsiClass)resolved : null;
      }

      @Override
      public PsiSubstitutor getSubstitutor() {
        return resolveResult.getSubstitutor();
      }

      @Override
      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      @Override
      public boolean isAccessible() {
        return resolveResult.isAccessible();
      }

      @Override
      public boolean isStaticsScopeCorrect() {
        return resolveResult.isStaticsOK();
      }

      @Nullable
      @Override
      public PsiElement getCurrentFileResolveScope() {
        return resolveResult.getCurrentFileResolveContext();
      }

      @Override
      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public PsiClassType rawType() {
    PsiClass clazz = resolve();
    if (clazz != null) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(clazz.getProject());
      return factory.createType(clazz, factory.createRawSubstitutor(clazz));
    }

    return this;
  }

  @Override
  @RequiredReadAction
  public String getPresentableText() {
    return PsiNameHelper.getPresentableText(myReferenceElement.getReferenceName(), PsiAnnotation.EMPTY_ARRAY, myReferenceElement.getTypeArguments());
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public String getCanonicalText() {
    return myReferenceElement.getCanonicalText();
  }

  @Override
  @RequiredReadAction
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  @RequiredReadAction
  public boolean isValid() {
    return myReferenceElement.isValid();
  }

  @Override
  @RequiredReadAction
  public boolean equalsToText(String text) {
    return text.endsWith(getPresentableText()) && //optimization
        text.equals(getCanonicalText());
  }

  @Nonnull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myReferenceElement.getResolveScope();
  }

  @Nonnull
  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Nonnull
  @Override
  public PsiClassType setLanguageLevel(@Nonnull LanguageLevel languageLevel) {
    return new GrClassReferenceType(myReferenceElement,languageLevel);
  }

  public GrReferenceElement getReference() {
    return myReferenceElement;
  }
}
