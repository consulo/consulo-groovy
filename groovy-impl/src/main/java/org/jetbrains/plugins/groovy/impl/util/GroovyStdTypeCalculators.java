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
package org.jetbrains.plugins.groovy.impl.util;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.application.util.RecursionManager;
import consulo.ide.impl.idea.openapi.util.NullableComputable;
import consulo.util.lang.function.PairFunction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Sergey Evdokimov
 */
public class GroovyStdTypeCalculators {
  private GroovyStdTypeCalculators() {
  }

  public static class ClosureTypeExtractor implements PairFunction<GrMethodCall, PsiMethod, PsiType> {
    @Override
    public PsiType fun(GrMethodCall methodCall, PsiMethod method) {
      GrExpression[] allArguments = PsiUtil.getAllArguments(methodCall);
      GrClosableBlock closure = null;

      for (GrExpression argument : allArguments) {
        if (argument instanceof GrClosableBlock) {
          closure = (GrClosableBlock)argument;
          break;
        }
      }

      if (closure == null) return null;

      final GrClosableBlock finalClosure = closure;

      return RecursionManager.doPreventingRecursion(methodCall, true, new NullableComputable<PsiType>() {
        @Override
        public PsiType compute() {
          PsiType returnType = finalClosure.getReturnType();
          if (returnType == PsiType.VOID) return null;
          return returnType;
        }
      });
    }
  }

  public static class TypeSameAsFirstArgument implements PairFunction<GrMethodCall, PsiMethod, PsiType>
  {

    @Override
    public PsiType fun(GrMethodCall methodCall, PsiMethod method) {
      GrArgumentList argumentList = methodCall.getArgumentList();
      if (argumentList == null) return null;

      GrExpression[] arguments = argumentList.getExpressionArguments();
      if (arguments.length == 0) return null;

      return arguments[0].getType();
    }
  }

}
