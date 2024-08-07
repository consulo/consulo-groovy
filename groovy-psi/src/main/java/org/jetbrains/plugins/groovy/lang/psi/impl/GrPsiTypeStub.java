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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeVisitor;
import consulo.language.psi.scope.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class GrPsiTypeStub extends PsiType {
  public GrPsiTypeStub() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  @Nonnull
  @Override
  public String getPresentableText() {
    return "?";
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return "?";
  }

  @Nonnull
  @Override
  public String getInternalCanonicalText() {
    return "?";
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean equalsToText(@Nonnull @NonNls String text) {
    return false;
  }

  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    return null;
  }

  @Nullable
  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Nonnull
  @Override
  public PsiType[] getSuperTypes() {
    return EMPTY_ARRAY;
  }
}
