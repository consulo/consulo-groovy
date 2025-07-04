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
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.java.language.psi.PsiMethod;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

/**
 * @author ven
 */
public interface GrCall extends GroovyPsiElement {
  @Nullable
  GrArgumentList getArgumentList();

  @Nonnull
  GrNamedArgument[] getNamedArguments();

  @Nonnull
  GrExpression[] getExpressionArguments();

  @Nonnull
  GrClosableBlock[] getClosureArguments();

  @Nullable
  GrNamedArgument addNamedArgument(GrNamedArgument namedArgument) throws IncorrectOperationException;

  @Nonnull
  GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument);

  @Nullable
  PsiMethod resolveMethod();

  @Nonnull
  GroovyResolveResult advancedResolve();

  @Nonnull
  GroovyResolveResult[] multiResolveGroovy(boolean incompleteCode);
}
