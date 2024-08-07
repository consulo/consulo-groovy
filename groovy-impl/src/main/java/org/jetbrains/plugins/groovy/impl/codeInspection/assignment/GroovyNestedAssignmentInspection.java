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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;

@ExtensionImpl
public class GroovyNestedAssignmentInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Nested assignment";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Nested assignment expression #loc";
  }

  @Nonnull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression grAssignmentExpression) {
      super.visitAssignmentExpression(grAssignmentExpression);
      final PsiElement parent = grAssignmentExpression.getParent();
      if (!(parent instanceof GrAssignmentExpression)) {
        return;
      }
      registerError(grAssignmentExpression);
    }
  }
}
