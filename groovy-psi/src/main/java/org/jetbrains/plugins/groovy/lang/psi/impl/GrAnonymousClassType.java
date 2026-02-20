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
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class GrAnonymousClassType extends GrLiteralClassType {
  private final GrAnonymousClassDefinition myAnonymous;

  public GrAnonymousClassType(@Nonnull LanguageLevel languageLevel,
                              @Nonnull GlobalSearchScope scope,
                              @Nonnull JavaPsiFacade facade,
                              @Nonnull GrAnonymousClassDefinition anonymous) {
    super(languageLevel, scope, facade);
    myAnonymous = anonymous;
  }

  @Nonnull
  @Override
  protected String getJavaClassName() {
    GrCodeReferenceElement ref = myAnonymous.getBaseClassReferenceGroovy();
    PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiClass) {
      return ((PsiClass)resolved).getQualifiedName();
    }
    else {
      return ref.getClassNameText();
    }
  }

  @Nonnull
  @Override
  public String getClassName() {
    return StringUtil.getShortName(getJavaClassName());
  }

  @Override
  public GrAnonymousClassDefinition resolve() {
    return myAnonymous;
  }

  @Nonnull
  @Override
  public PsiType[] getParameters() {
    return myAnonymous.getBaseClassReferenceGroovy().getTypeArguments();
  }

  @Nonnull
  @Override
  public PsiClassType setLanguageLevel(@Nonnull LanguageLevel languageLevel) {
    return new GrAnonymousClassType(languageLevel, myScope, myFacade, myAnonymous);
  }

  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  public boolean isValid() {
    return myAnonymous.isValid();
  }

  @Override
  public String toString() {
    return "AnonymousType:" + getPresentableText();
  }

  @Nonnull
  public PsiClassType getSimpleClassType() {
    return new GrClassReferenceType(myAnonymous.getBaseClassReferenceGroovy(), myLanguageLevel);
  }
}
