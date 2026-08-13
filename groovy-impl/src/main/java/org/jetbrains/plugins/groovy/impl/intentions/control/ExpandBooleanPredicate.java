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
package org.jetbrains.plugins.groovy.impl.intentions.control;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

class ExpandBooleanPredicate implements PsiElementPredicate {
  @Override
  @RequiredReadAction
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrStatement statement)) {
      return false;
    }
    return isBooleanReturn(statement) || isBooleanAssignment(statement);
  }

  @RequiredReadAction
  public static boolean isBooleanReturn(GrStatement statement) {
    if (!(statement instanceof GrReturnStatement returnStatement)) {
      return false;
    }
    GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return false;
    }
    if (returnValue instanceof GrLiteral) {
      return false;
    }
    PsiType returnType = returnValue.getType();
    if (returnType == null) {
      return false;
    }
    return returnType.equals(PsiType.BOOLEAN) || returnType.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN);
  }

  @RequiredReadAction
  public static boolean isBooleanAssignment(GrStatement expression) {
    if (!(expression instanceof GrAssignmentExpression assignment)) {
      return false;
    }
    GrExpression rhs = assignment.getRValue();
    if (rhs == null) {
      return false;
    }
    if (rhs instanceof GrLiteral) {
      return false;
    }
    PsiType assignmentType = rhs.getType();
    if (assignmentType == null) {
      return false;
    }
    return assignmentType.equals(PsiType.BOOLEAN) || assignmentType.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN);
  }
}
