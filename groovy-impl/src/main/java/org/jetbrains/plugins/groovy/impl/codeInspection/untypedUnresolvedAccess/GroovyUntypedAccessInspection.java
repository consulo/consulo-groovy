/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.codeInspection.untypedUnresolvedAccess;

import com.intellij.java.language.psi.PsiClassType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static org.jetbrains.plugins.groovy.impl.annotator.GrHighlightUtil.isDeclarationAssignment;

/**
 * @author Maxim.Medvedev
 */
public class GroovyUntypedAccessInspection extends BaseInspection {

  protected BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Access to untyped expression";
  }

  @Override
  protected String buildErrorString(Object... args) {
    return "Cannot determine type of '#ref'";
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitReferenceExpression(GrReferenceExpression refExpr) {
      super.visitReferenceExpression(refExpr);
      GroovyResolveResult resolveResult = refExpr.advancedResolve();

      PsiElement resolved = resolveResult.getElement();
      if (resolved != null) {
        if (isDeclarationAssignment(refExpr) || resolved instanceof PsiJavaPackage) return;
      }
      else {
        GrExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier == null && isDeclarationAssignment(refExpr)) return;
      }

      final PsiType refExprType = refExpr.getType();
      if (refExprType == null) {
        if (resolved != null) {
          registerError(refExpr);
        }
      }
      else if (refExprType instanceof PsiClassType && ((PsiClassType)refExprType).resolve() == null) {
        registerError(refExpr);
      }
    }
  }
}
