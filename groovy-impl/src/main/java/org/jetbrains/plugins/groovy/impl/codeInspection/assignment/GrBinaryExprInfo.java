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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 05/02/14
 */
public class GrBinaryExprInfo implements CallInfo<GrBinaryExpression> {
  private final GrBinaryExpression myExpr;

  public GrBinaryExprInfo(GrBinaryExpression expr) {
    myExpr = expr;
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return null;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    GrExpression operand = myExpr.getRightOperand();

    return new PsiType[]{operand != null ? operand.getType() : null};
  }

  @Nullable
  @Override
  public GrExpression getInvokedExpression() {
    return myExpr.getLeftOperand();
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return myExpr.getLeftOperand().getType();
  }

  @Nonnull
  @Override
  public PsiElement getHighlightElementForCategoryQualifier() {
    return myExpr.getOperationToken();
  }

  @Nonnull
  @Override
  public PsiElement getElementToHighlight() {
    return myExpr.getOperationToken();
  }

  @Nonnull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve());
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolve() {
    return myExpr.multiResolve(false);
  }

  @Nonnull
  @Override
  public GrBinaryExpression getCall() {
    return myExpr;
  }

  @Nonnull
  @Override
  public GrExpression[] getExpressionArguments() {
    GrExpression right = myExpr.getRightOperand();
    if (right != null) {
      return new GrExpression[]{right};
    }
    else {
      return GrExpression.EMPTY_ARRAY;
    }
  }

  @Nonnull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    return GrNamedArgument.EMPTY_ARRAY;
  }
}
