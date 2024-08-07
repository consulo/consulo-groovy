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
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.LibraryUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyMethodParameterCountInspection extends GroovyMethodMetricInspection {

  @Nonnull
  public String getDisplayName() {
    return "Method with too many parameters";
  }

  @Nonnull
  public String getGroupDisplayName() {
    return METHOD_METRICS;
  }

  protected int getDefaultLimit() {
    return 5;
  }

  protected String getConfigurationLabel() {
    return "Maximum number of parameters:";
  }

  public String buildErrorString(Object... args) {
    return "Method '#ref' contains too many parameters (" + args[0] + '>' + args[1] + ')';
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod grMethod) {
      super.visitMethod(grMethod);
      final GrParameter[] parameters = grMethod.getParameters();
      final int limit = getLimit();
      if (parameters == null || parameters.length <= limit) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(grMethod)) {
        return;
      }
      registerMethodError(grMethod, parameters.length, limit);
    }
  }
}
