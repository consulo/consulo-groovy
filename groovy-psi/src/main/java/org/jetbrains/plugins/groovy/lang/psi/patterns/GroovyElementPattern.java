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
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.java.language.patterns.PsiJavaElementPattern;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.InitialPatternCondition;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PsiNamePatternCondition;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;

public class GroovyElementPattern<T extends GroovyPsiElement,Self extends GroovyElementPattern<T,Self>> extends PsiJavaElementPattern<T,Self> {
  public GroovyElementPattern(Class<T> aClass) {
    super(aClass);
  }

  public GroovyElementPattern(@Nonnull InitialPatternCondition<T> condition) {
    super(condition);
  }

  @Override
  public Self methodCallParameter(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<>("methodCallParameter") {
      @Override
      @RequiredReadAction
      public boolean accepts(@Nonnull T literal, ProcessingContext context) {
        if (literal instanceof GrExpression expr
          && literal.getParent() instanceof GrArgumentList argList
          && argList.getExpressionArgumentIndex(expr) == index
          && argList.getParent() instanceof GrCall call) {
          GroovyPsiElement expression = switch (call) {
            case GrMethodCall methodCall -> methodCall.getInvokedExpression();
            case GrNewExpression newExpr -> newExpr.getReferenceElement();
            default -> null;
          };

          if (expression instanceof GrReferenceElement ref) {
            PsiNamePatternCondition nameCondition = null;

            for (PatternCondition<?> condition : methodPattern.getCondition().getConditions()) {
              if (condition instanceof PsiNamePatternCondition npc) {
                nameCondition = npc;
                break;
              }
            }

            if (nameCondition != null && "withName".equals(nameCondition.getDebugMethodName())) {
              String methodName = ref.getReferenceName();
              //noinspection unchecked
              if (methodName != null && !nameCondition.getNamePattern().accepts(methodName, context)) {
                return false;
              }
            }

            for (GroovyResolveResult result : ref.multiResolve(false)) {
              PsiElement psiElement = result.getElement();
              if (methodPattern.getCondition().accepts(psiElement, context)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    });
  }

  public static class Capture<T extends GroovyPsiElement> extends GroovyElementPattern<T, Capture<T>> {
    public Capture(Class<T> aClass) {
      super(aClass);
    }

    public Capture(@Nonnull InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}