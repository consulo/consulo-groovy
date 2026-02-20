/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import com.intellij.java.language.psi.CommonClassNames;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import org.jetbrains.plugins.groovy.impl.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;


class IndexingMethodConversionPredicate implements PsiElementPredicate {
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) {
      return false;
    }

    if (ErrorUtil.containsError(element)) {
      return false;
    }
    GrMethodCallExpression callExpression = (GrMethodCallExpression) element;
    GrArgumentList argList = callExpression.getArgumentList();
    if (argList == null) {
      return false;
    }
    GrExpression[] arguments = argList.getExpressionArguments();

    GrExpression invokedExpression = callExpression.getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression)) {
      return false;
    }
    GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
    GrExpression qualifier = referenceExpression.getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    IElementType referenceType = referenceExpression.getDotTokenType();
    if (!GroovyTokenTypes.mDOT.equals(referenceType)) {
      return false;
    }
    String methodName = referenceExpression.getReferenceName();
    if ("getAt".equals(methodName)) {
      return arguments.length == 1;
    }
    if ("get".equals(methodName)) {
      PsiType qualifierType = qualifier.getType();
      if (!isMap(qualifierType)) {
        return false;
      }
      return arguments.length == 1;
    } else if ("setAt".equals(methodName)) {
      return arguments.length == 2;
    } else if ("put".equals(methodName)) {
      PsiType qualifierType = qualifier.getType();
      if (!isMap(qualifierType)) {
        return false;
      }
      return arguments.length == 2;
    }
    return false;
  }

  private static boolean isMap(PsiType type) {
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
  }

}
