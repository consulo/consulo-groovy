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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.function.Function;

/**
 * Created by Max Medvedev on 12/20/13
 */
public class GrBinaryExpressionTypeCalculator implements Function<GrBinaryFacade, PsiType> {
  public static final Function<GrBinaryFacade, PsiType> INSTANCE = new GrBinaryExpressionTypeCalculator();

  @Override
  @Nullable
  public PsiType apply(GrBinaryFacade e) {
    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(e.multiResolve(false));
    if (resolveResult.getElement() != null) {
      return ResolveUtil.extractReturnTypeFromCandidate(resolveResult, e.getPsiElement(), new PsiType[]{getRightType(e)});
    }
    return null;
  }

  @Nullable
  protected static PsiType getRightType(GrBinaryFacade e) {
    final GrExpression rightOperand = e.getRightOperand();
    return rightOperand == null ? null : rightOperand.getType();
  }

}
