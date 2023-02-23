/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterData;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import javax.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
public class GrExpressionWrapper implements IntroduceParameterData.ExpressionWrapper {
  private final GrExpression myExpression;

  public GrExpressionWrapper(GrExpression expression) {
    myExpression = expression;
  }

  @Nonnull
  @Override
  public String getText() {
    return myExpression.getText();
  }

  @Override
  public PsiType getType() {
    return myExpression.getType();
  }

  @Nonnull
  @Override
  public PsiElement getExpression() {
    return myExpression;
  }
}
