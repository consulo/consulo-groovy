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
  public GroovyElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public GroovyElementPattern(@Nonnull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  public Self methodCallParameter(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<T>("methodCallParameter") {
      public boolean accepts(@Nonnull final T literal, final ProcessingContext context) {
        final PsiElement parent = literal.getParent();
        if (parent instanceof GrArgumentList) {
          if (!(literal instanceof GrExpression)) return false;

          final GrArgumentList psiExpressionList = (GrArgumentList)parent;
          if (psiExpressionList.getExpressionArgumentIndex((GrExpression)literal) != index) return false;

          final PsiElement element = psiExpressionList.getParent();
          if (element instanceof GrCall) {
            final GroovyPsiElement expression =
              element instanceof GrMethodCall ? ((GrMethodCall)element).getInvokedExpression() :
              element instanceof GrNewExpression? ((GrNewExpression)element).getReferenceElement() :
              null;


            if (expression instanceof GrReferenceElement) {
              final GrReferenceElement ref = (GrReferenceElement)expression;

              PsiNamePatternCondition nameCondition = null;

              for (PatternCondition<?> condition : methodPattern.getCondition().getConditions()) {
                if (condition instanceof PsiNamePatternCondition) {
                  nameCondition = (PsiNamePatternCondition)condition;
                  break;
                }
              }

              if (nameCondition != null && "withName".equals(nameCondition.getDebugMethodName())) {
                final String methodName = ref.getReferenceName();
                //noinspection unchecked
                if (methodName != null && !nameCondition.getNamePattern().accepts(methodName, context)) {
                  return false;
                }
              }

              for (GroovyResolveResult result : ref.multiResolve(false)) {
                final PsiElement psiElement = result.getElement();
                if (methodPattern.getCondition().accepts(psiElement, context)) {
                  return true;
                }
              }
            }
          }
        }
        return false;
      }
    });
  }

  public static class Capture<T extends GroovyPsiElement> extends GroovyElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@Nonnull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }

}