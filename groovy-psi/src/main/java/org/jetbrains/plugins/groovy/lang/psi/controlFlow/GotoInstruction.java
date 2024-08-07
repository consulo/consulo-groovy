/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;

import jakarta.annotation.Nullable;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

/**
 * @author Max Medvedev
 */
public abstract class GotoInstruction extends InstructionImpl {
  @Nonnull
  private final ConditionInstruction myCondition;

  public GotoInstruction(@Nullable PsiElement element, @Nonnull ConditionInstruction condition) {
    super(element);
    myCondition = condition;
  }

  @Nonnull
  public ConditionInstruction getCondition() {
    return myCondition;
  }

  @Override
  protected String getElementPresentation() {
    return " Positive goto instruction, condition=" + myCondition.num() + getElement();
  }
}
