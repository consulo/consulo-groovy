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
package org.jetbrains.plugins.groovy.impl.intentions.utils;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;

public class ConditionalUtils {

  private ConditionalUtils() {
    super();
  }

  public static GrStatement stripBraces(GrStatement branch) {
    if (branch instanceof GrBlockStatement) {
      GrBlockStatement block = (GrBlockStatement) branch;
      GrOpenBlock codeBlock = block.getBlock();
      GrStatement[] statements = codeBlock.getStatements();
      if (statements.length == 1) {
        return statements[0];
      } else {
        return block;
      }
    } else {
      return branch;
    }
  }

  public static boolean isReturn(GrStatement statement, @NonNls String value) {
    if (statement == null) {
      return false;
    }
    if (!(statement instanceof GrReturnStatement)) {
      return false;
    }
    GrReturnStatement returnStatement =
        (GrReturnStatement) statement;
    GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return false;
    }
    String returnValueText = returnValue.getText();
    return value.equals(returnValueText);
  }

  public static boolean isAssignment(GrStatement statement, @NonNls String value) {
    if (statement == null) {
      return false;
    }
    if (!(statement instanceof GrExpression)) {
      return false;
    }
    GrExpression expression = (GrExpression) statement;
    if (!(expression instanceof GrAssignmentExpression)) {
      return false;
    }
    GrAssignmentExpression assignment =
        (GrAssignmentExpression) expression;
    GrExpression rhs = assignment.getRValue();
    if (rhs == null) {
      return false;
    }
    String rhsText = rhs.getText();
    return value.equals(rhsText);
  }

  public static boolean isAssignment(GrStatement statement) {
    return statement instanceof GrAssignmentExpression;
  }
}
