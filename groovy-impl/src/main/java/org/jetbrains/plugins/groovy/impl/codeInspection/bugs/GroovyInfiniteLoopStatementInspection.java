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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;

@ExtensionImpl
public class GroovyInfiniteLoopStatementInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Infinite loop statement";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "<code>#ref</code> statement cannot complete without throwing an exception #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitWhileStatement(GrWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      if (ControlFlowUtils.statementMayCompleteNormally(whileStatement)) {
        return;
      }
      if (ControlFlowUtils.statementContainsReturn(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }
  }
}