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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nonnull;

/**
 * @author ilyas
 */
public interface GrArgumentLabel extends GroovyPsiElement, PsiPolyVariantReference
{

  GrArgumentLabel[] EMPTY_ARRAY = new GrArgumentLabel[0];

  @Nonnull
  PsiElement getNameElement();

  @Nullable
  /**
   * returns expression which is put into parentheses.
   */
  GrExpression getExpression();

  @Nullable
  String getName();

  PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException;

  @Nullable
  PsiType getExpectedArgumentType();

  @Nullable
  PsiType getLabelType();

  GrNamedArgument getNamedArgument();

  @Nonnull
  GroovyResolveResult[] multiResolve(boolean incomplete);

  @Nonnull
  GroovyResolveResult advancedResolve();
}
