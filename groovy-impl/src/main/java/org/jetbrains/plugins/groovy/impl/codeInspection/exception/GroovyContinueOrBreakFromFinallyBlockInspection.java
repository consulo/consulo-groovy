/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.exception;

import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class GroovyContinueOrBreakFromFinallyBlockInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return ERROR_HANDLING;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "'continue' or 'break' inside 'finally' block";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "'#ref' inside 'finally' block #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitContinueStatement(GrContinueStatement continueStatement) {

      super.visitContinueStatement(continueStatement);
      if (!ControlFlowUtils.isInFinallyBlock(continueStatement)) {
        return;
      }
      final GrStatement continuedStatement = continueStatement.findTargetStatement();
      if (continuedStatement == null) {
        return;
      }
      if (ControlFlowUtils.isInFinallyBlock(continuedStatement)) {
        return;
      }
      registerStatementError(continueStatement);
    }

    public void visitBreakStatement(GrBreakStatement breakStatement) {

      super.visitBreakStatement(breakStatement);
      if (!ControlFlowUtils.isInFinallyBlock(breakStatement)) {
        return;
      }
      final GrStatement target = breakStatement.findTargetStatement();
      if (target == null) {
        return;
      }
      if (ControlFlowUtils.isInFinallyBlock(target)) {
        return;
      }
      registerStatementError(breakStatement);
    }
  }
}