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
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class GroovyEmptyFinallyBlockInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return ERROR_HANDLING;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Empty 'finally' block";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Empty '#ref' block #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitFinallyClause(GrFinallyClause finallyClause) {
      super.visitFinallyClause(finallyClause);
      final GrOpenBlock body = finallyClause.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }
      registerError(finallyClause.getFirstChild());
    }

    private static boolean isEmpty(GrOpenBlock body) {
      final GrStatement[] statements = body.getStatements();
      return statements.length == 0;
    }
  }
}