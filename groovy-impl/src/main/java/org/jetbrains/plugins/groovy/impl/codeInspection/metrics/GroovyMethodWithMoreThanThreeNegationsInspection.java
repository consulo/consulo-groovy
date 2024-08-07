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
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyMethodWithMoreThanThreeNegationsInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return "Method with more than three negations";
  }

  @Nonnull
  public String getGroupDisplayName() {
    return METHOD_METRICS;
  }

  public String buildErrorString(Object... args) {
    return "Method '#ref' has too many negations (" + args[0] + " > 3)";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod grMethod) {
      super.visitMethod(grMethod);
      final NegationCountVisitor visitor = new NegationCountVisitor();
      final GrOpenBlock body = grMethod.getBlock();
      if (body == null) {
        return;
      }
      body.accept(visitor);
      final int numNegations = visitor.getNegationCount();
      if (numNegations <= 3) {
        return;
      }
      registerMethodError(grMethod, numNegations);
    }
  }
}