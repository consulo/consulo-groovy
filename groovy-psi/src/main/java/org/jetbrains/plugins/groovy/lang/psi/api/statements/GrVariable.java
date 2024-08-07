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

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import jakarta.annotation.Nullable;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 11.04.2007
 */
public interface GrVariable extends PsiVariable, GrNamedElement {
  GrVariable[] EMPTY_ARRAY = new GrVariable[0];
  ArrayFactory<GrVariable> ARRAY_FACTORY = new ArrayFactory<GrVariable>() {
    @Nonnull
    @Override
    public GrVariable[] create(int count) {
      return new GrVariable[count];
    }
  };

  @Nonnull
  String getName();

  @Nullable
  GrExpression getInitializerGroovy();

  void setType(@Nullable PsiType type) throws IncorrectOperationException;

  @Nullable
  GrTypeElement getTypeElementGroovy();

  @Nullable
  PsiType getTypeGroovy();

  @Nullable
  PsiType getDeclaredType();

  @Nullable
  GrModifierList getModifierList();

  void setInitializerGroovy(@Nullable GrExpression initializer);
}
