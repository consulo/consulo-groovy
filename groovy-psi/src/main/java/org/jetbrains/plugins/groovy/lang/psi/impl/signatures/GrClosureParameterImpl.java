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

import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
* Created by Max Medvedev on 14/03/14
*/
class GrClosureParameterImpl implements GrClosureParameter {
  private final PsiParameter myParameter;
  private final PsiSubstitutor mySubstitutor;

  public GrClosureParameterImpl(@Nonnull PsiParameter parameter) {
    this(parameter, PsiSubstitutor.EMPTY);
  }

  public GrClosureParameterImpl(@Nonnull PsiParameter parameter, @Nonnull PsiSubstitutor substitutor) {
    myParameter = parameter;
    mySubstitutor = substitutor;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return mySubstitutor.substitute(myParameter.getType());
  }

  @Override
  public boolean isOptional() {
    return myParameter instanceof GrParameter && ((GrParameter)myParameter).isOptional();
  }

  @Nullable
  @Override
  public GrExpression getDefaultInitializer() {
    return myParameter instanceof GrParameter ? ((GrParameter)myParameter).getInitializerGroovy() : null;
  }

  @Override
  public boolean isValid() {
    return myParameter.isValid();
  }

  @Nullable
  @Override
  public String getName() {
    return myParameter.getName();
  }
}
