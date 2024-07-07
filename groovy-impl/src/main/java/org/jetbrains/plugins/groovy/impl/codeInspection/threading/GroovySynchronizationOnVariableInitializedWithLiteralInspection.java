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
package org.jetbrains.plugins.groovy.impl.codeInspection.threading;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import jakarta.annotation.Nonnull;

public class GroovySynchronizationOnVariableInitializedWithLiteralInspection extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return THREADING_ISSUES;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Synchronization on variable initialized with literal";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Synchronization on variable '#ref', which was initialized with a literal #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
      super.visitSynchronizedStatement(synchronizedStatement);
      final GrExpression lock = synchronizedStatement.getMonitor();
      if (lock == null || !(lock instanceof GrReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)lock).resolve();
      if (referent instanceof GrVariable) {
        final GrVariable variable = (GrVariable)referent;
        final GrExpression initializer = variable.getInitializerGroovy();
        if (initializer == null) {
          return;
        }
        if (!(initializer instanceof GrLiteral)) {
          return;
        }
        registerError(lock);
      }
      else if (referent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)referent;
        final PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
          return;
        }
        if (!(initializer instanceof PsiLiteralExpression)) {
          return;
        }
        registerError(lock);
      }
    }

  }

}