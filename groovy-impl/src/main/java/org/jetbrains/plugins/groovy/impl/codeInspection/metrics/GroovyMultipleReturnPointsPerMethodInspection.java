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
package org.jetbrains.plugins.groovy.impl.codeInspection.metrics;

import com.intellij.java.language.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;

public class GroovyMultipleReturnPointsPerMethodInspection
    extends GroovyMethodMetricInspection {
  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return METHOD_METRICS;
  }

  @Nonnull
  public String getDisplayName() {
    return "Method with multiple return points";
  }

  protected int getDefaultLimit() {
    return 1;
  }

  protected String getConfigurationLabel() {
    return "Return point limit:";
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer returnPointCount = (Integer) infos[0];
    return "<code>#ref</code> has " + returnPointCount + " return points #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new MultipleReturnPointsVisitor();
  }

  private class MultipleReturnPointsVisitor extends BaseInspectionVisitor {

    public void visitMethod(@Nonnull GrMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final int returnPointCount = calculateReturnPointCount(method);
      if (returnPointCount <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(returnPointCount));
    }

    private int calculateReturnPointCount(GrMethod method) {
      final ReturnPointCountVisitor visitor =
          new ReturnPointCountVisitor();
      method.accept(visitor);
      final int count = visitor.getCount();
      if (!mayFallThroughBottom(method)) {
        return count;
      }
      final GrCodeBlock body = method.getBlock();
      if (body == null) {
        return count;
      }
      final GrStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return count + 1;
      }
      final GrStatement lastStatement =
          statements[statements.length - 1];
      if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
        return count + 1;
      }
      return count;
    }

    private boolean mayFallThroughBottom(GrMethod method) {
      if (method.isConstructor()) {
        return true;
      }
      final PsiType returnType = method.getReturnType();
      return PsiType.VOID.equals(returnType);
    }
  }
}