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
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
* Created by Max Medvedev on 26/02/14
*/
class GrClosableSignatureImpl implements GrClosureSignature {
  private final GrClosableBlock myBlock;

  public GrClosableSignatureImpl(GrClosableBlock block) {
    myBlock = block;
  }

  @Nonnull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return PsiSubstitutor.EMPTY;
  }

  @Nonnull
  @Override
  public GrClosureParameter[] getParameters() {
    GrParameter[] parameters = myBlock.getAllParameters();

    return ContainerUtil.map(parameters, parameter -> createClosureParameter(parameter), new GrClosureParameter[parameters.length]);
  }

  @Nonnull
  protected GrClosureParameter createClosureParameter(@Nonnull GrParameter parameter) {
    return new GrClosureParameterImpl(parameter);
  }

  @Override
  public int getParameterCount() {
    return myBlock.getAllParameters().length;
  }

  @Override
  public boolean isVarargs() {
    GrParameter last = ArrayUtil.getLastElement(myBlock.getAllParameters());
    return last != null && last.getType() instanceof PsiArrayType;
  }

  @Nullable
  @Override
  public PsiType getReturnType() {
    return myBlock.getReturnType();
  }

  @Override
  public boolean isCurried() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myBlock.isValid();
  }

  @Nullable
  @Override
  public GrSignature curry(@Nonnull PsiType[] args, int position, @Nonnull PsiElement context) {
    return GrClosureSignatureUtil.curryImpl(this, args, position, context);
  }

  @Override
  public void accept(@Nonnull GrSignatureVisitor visitor) {
    visitor.visitClosureSignature(this);
  }
}
