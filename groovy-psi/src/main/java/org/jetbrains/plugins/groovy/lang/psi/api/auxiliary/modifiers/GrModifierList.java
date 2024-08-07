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

package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers;

import com.intellij.java.language.psi.PsiModifierList;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

import jakarta.annotation.Nonnull;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public interface GrModifierList extends GroovyPsiElement, PsiModifierList
{

  @Nonnull
  PsiElement[] getModifiers();

  boolean hasExplicitVisibilityModifiers();

  @Nonnull
  GrAnnotation[] getAnnotations();


  @Override
  boolean hasModifierProperty(@GrModifier.GrModifierConstant @Nonnull @NonNls String name);

  @Override
  boolean hasExplicitModifier(@GrModifier.GrModifierConstant @Nonnull @NonNls String name);

  @Override
  void setModifierProperty(@GrModifier.GrModifierConstant @Nonnull @NonNls String name, boolean value) throws IncorrectOperationException;

  @Nonnull
  GrAnnotation[] getRawAnnotations();
}
