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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks;

import javax.annotation.Nonnull;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;

import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

/**
 * @author ilyas
 */
public interface GrClosableBlock extends GrExpression, GrCodeBlock, GrParametersOwner {
  GrClosableBlock[] EMPTY_ARRAY = new GrClosableBlock[0];

  String OWNER_NAME = "owner";
  String IT_PARAMETER_NAME = "it";

  @Nonnull
  GrParameterList getParameterList();

  GrParameter addParameter(GrParameter parameter);

  boolean hasParametersSection();

  @javax.annotation.Nullable
  PsiType getReturnType();

  GrParameter[] getAllParameters();

  @javax.annotation.Nullable
  PsiElement getArrow();

  boolean isVarArgs();

  boolean processClosureDeclarations(final @Nonnull PsiScopeProcessor placeProcessor,
                                     final @Nonnull PsiScopeProcessor nonCodeProcessor,
                                     final @Nonnull ResolveState _state,
                                     final @Nullable PsiElement lastParent,
                                     final @Nonnull PsiElement place);
}
