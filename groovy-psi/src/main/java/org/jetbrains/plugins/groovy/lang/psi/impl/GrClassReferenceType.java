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
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.scope.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
  public PsiClass resolve() {
    ResolveResult[] results = multiResolve();
    if (results.length == 1) {
      PsiElement only = results[0].getElement();
      return only instanceof PsiClass ? (PsiClass) only : null;
    }

    return null;
  }

  //reference resolve is cached
  private GroovyResolveResult[] multiResolve() {
    return myReferenceElement.multiResolve(false);
  }

  @Nullable
  public String getClassName() {
    final PsiClass resolved = resolve();
    if (resolved != null) return resolved.getName();
    return myReferenceElement.getReferenceName();
  }

  @Nonnull
  public PsiType[] getParameters() {
    return myReferenceElement.getTypeArguments();
  }

  @Nonnull
  public ClassResolveResult resolveGenerics() {
    final GroovyResolveResult resolveResult = myReferenceElement.advancedResolve();
    return new ClassResolveResult() {
      public PsiClass getElement() {
        final PsiElement resolved = resolveResult.getElement();
        return resolved instanceof PsiClass ? (PsiClass)resolved : null;
      }

      public PsiSubstitutor getSubstitutor() {
        return resolveResult.getSubstitutor();
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      public boolean isAccessible() {
        return resolveResult.isAccessible();
      }

      public boolean isStaticsScopeCorrect() {
        return resolveResult.isStaticsOK();
      }

      @Nullable
      public PsiElement getCurrentFileResolveScope() {
        return resolveResult.getCurrentFileResolveContext();
      }

      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @Nonnull
  public PsiClassType rawType() {
    final PsiClass clazz = resolve();
    if (clazz != null) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(clazz.getProject());
      return factory.createType(clazz, factory.createRawSubstitutor(clazz));
    }

    return this;
  }

  public String getPresentableText() {
    return PsiNameHelper.getPresentableText(myReferenceElement.getReferenceName(), PsiAnnotation.EMPTY_ARRAY, myReferenceElement.getTypeArguments());
  }

  @Nonnull
  public String getCanonicalText() {
    return myReferenceElement.getCanonicalText();
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  public boolean isValid() {
    return myReferenceElement.isValid();
  }

  public boolean equalsToText(@NonNls String text) {
    return text.endsWith(getPresentableText()) && //optimization
        text.equals(getCanonicalText());
  }

  @Nonnull
  public GlobalSearchScope getResolveScope() {
    return myReferenceElement.getResolveScope();
  }

  @Nonnull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Nonnull
  public PsiClassType setLanguageLevel(@Nonnull final LanguageLevel languageLevel) {
    return new GrClassReferenceType(myReferenceElement,languageLevel);
  }

  public GrReferenceElement getReference() {
    return myReferenceElement;
  }
}
