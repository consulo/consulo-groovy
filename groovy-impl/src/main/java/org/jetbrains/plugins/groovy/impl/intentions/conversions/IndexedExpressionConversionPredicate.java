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

import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;
import org.jetbrains.plugins.groovy.impl.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;


class IndexedExpressionConversionPredicate implements PsiElementPredicate {
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrIndexProperty)) return false;

    if (ErrorUtil.containsError(element)) return false;

    GrIndexProperty arrayIndexExpression = (GrIndexProperty) element;
    PsiElement lastChild = arrayIndexExpression.getLastChild();
    if (!(lastChild instanceof GrArgumentList)) return false;

    GrArgumentList argList = (GrArgumentList) lastChild;

    GrExpression[] arguments = argList.getExpressionArguments();
    if (arguments.length != 1) return false;

    PsiElement parent = element.getParent();
    if (!(parent instanceof GrAssignmentExpression)) {
      return true;
    }
    GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) parent;
    GrExpression rvalue = assignmentExpression.getRValue();
    if (rvalue == null) return false;

    if (rvalue.equals(element)) return true;

    IElementType operator = assignmentExpression.getOperationTokenType();
    return GroovyTokenTypes.mASSIGN.equals(operator);
  }

}
