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

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public class GroovyResultOfObjectAllocationIgnoredInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Result of object allocation ignored";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Result of <code>new #ref" + (args[0].equals(new Integer(0)) ? "()" : "[]") + "</code> is ignored #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitNewExpression(GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      final GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;      //new expression is not correct so we shouldn't check it
      
      final PsiElement parent = newExpression.getParent();

      if (parent instanceof GrCodeBlock || parent instanceof GroovyFile) {
        if (parent instanceof GrOpenBlock || parent instanceof GrClosableBlock) {
          if (ControlFlowUtils.openBlockCompletesWithStatement(((GrCodeBlock)parent), newExpression)) {
            return;
          }
        }
        registerError(refElement, newExpression.getArrayCount());
      }
    }
  }
}