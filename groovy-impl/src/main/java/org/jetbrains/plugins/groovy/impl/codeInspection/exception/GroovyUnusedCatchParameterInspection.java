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
import consulo.ide.impl.idea.codeInsight.daemon.impl.quickfix.RenameElementFix;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

@ExtensionImpl
public class GroovyUnusedCatchParameterInspection extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return ERROR_HANDLING;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Unused catch parameter";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitCatchClause(GrCatchClause catchClause) {
      super.visitCatchClause(catchClause);
      final GrOpenBlock block = catchClause.getBody();
      if (block == null) {
        return;
      }
      final GrParameter parameter = catchClause.getParameter();
      if (parameter == null) {
        return;
      }
      if (GrExceptionUtil.ignore(parameter)) return;
      final CatchParameterUsedVisitor visitor = new CatchParameterUsedVisitor(parameter);
      block.accept(visitor);
      if (!visitor.isUsed()) {
        final PsiElement nameIdentifier = parameter.getNameIdentifierGroovy();
        registerError(nameIdentifier, "Unused catch parameter '#ref' #loc", new LocalQuickFix[]{new RenameElementFix(parameter, "ignored")},
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }
  }
}