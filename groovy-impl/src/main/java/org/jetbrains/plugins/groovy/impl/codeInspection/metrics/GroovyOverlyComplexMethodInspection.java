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

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyOverlyComplexMethodInspection extends GroovyMethodMetricInspection {

  @Nonnull
  public String getDisplayName() {
    return "Overly complex method";
  }

  @Nonnull
  public String getGroupDisplayName() {
    return METHOD_METRICS;
  }

  protected int getDefaultLimit() {
    return 10;
  }

  protected String getConfigurationLabel() {
    return "Method complexity limit:";
  }

  public String buildErrorString(Object... args) {
    return "Method '#ref' is overly complex ( cyclomatic complexity =" + args[0] + '>' + args[1] + ')';
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod grMethod) {
      super.visitMethod(grMethod);
      final int limit = getLimit();
      final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
      final GrOpenBlock body = grMethod.getBlock();
      if (body == null) {
        return;
      }
      body.accept(visitor);
      final int complexity = visitor.getComplexity();
      if (complexity <= limit) {
        return;
      }
      registerMethodError(grMethod, complexity, limit);
    }
  }
}