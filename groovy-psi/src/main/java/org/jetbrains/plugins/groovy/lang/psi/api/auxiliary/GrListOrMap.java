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

package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary;

import com.intellij.java.language.psi.PsiArrayInitializerMemberValue;
import consulo.language.psi.PsiElement;
import consulo.navigation.NavigationItem;
import consulo.util.dataholder.UserDataHolderEx;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;

import javax.annotation.Nonnull;

/**
 * @author ilyas
 */
public interface GrListOrMap extends UserDataHolderEx, Cloneable, PsiElement, NavigationItem, GrExpression,
  PsiArrayInitializerMemberValue, GrNamedArgumentsOwner {
  /*
   * Use for list
   */
  @Override
  @Nonnull
  GrExpression[] getInitializers();

  boolean isMap();

  boolean isEmpty();

  PsiElement getLBrack();

  PsiElement getRBrack();
}
