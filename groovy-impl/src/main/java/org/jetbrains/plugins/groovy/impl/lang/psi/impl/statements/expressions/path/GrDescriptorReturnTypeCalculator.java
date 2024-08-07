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
package org.jetbrains.plugins.groovy.impl.lang.psi.impl.statements.expressions.path;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;

import jakarta.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class GrDescriptorReturnTypeCalculator extends GrCallExpressionTypeCalculator {

  @Override
  public PsiType calculateReturnType(@Nonnull GrMethodCall callExpression, @Nonnull PsiMethod method) {
    for (GroovyMethodInfo methodInfo : GroovyMethodInfo.getInfos(method)) {
      String returnType = methodInfo.getReturnType();
      if (returnType != null) {
        if (methodInfo.isApplicable(method)) {
          return JavaPsiFacade.getElementFactory(callExpression.getProject()).createTypeFromText(returnType, callExpression);
        }
      }
      else {
        if (methodInfo.isReturnTypeCalculatorDefined()) {
          if (methodInfo.isApplicable(method)) {
            PsiType result = methodInfo.getReturnTypeCalculator().fun(callExpression, method);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }

    return null;
  }
}
