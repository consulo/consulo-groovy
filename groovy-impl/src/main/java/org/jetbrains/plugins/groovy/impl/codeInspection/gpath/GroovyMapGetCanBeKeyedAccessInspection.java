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
package org.jetbrains.plugins.groovy.impl.codeInspection.gpath;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class GroovyMapGetCanBeKeyedAccessInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return GPATH;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Call to Map.get can be keyed access";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to '#ref' can be keyed access #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  public GroovyFix buildFix(PsiElement location) {
    return new ReplaceWithPropertyAccessFix();
  }

  private static class ReplaceWithPropertyAccessFix extends GroovyFix {

    @Nonnull
    public String getName() {
      return "Replace with keyed access";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement referenceName = descriptor.getPsiElement();
      final GrReferenceExpression invokedExpression = (GrReferenceExpression) referenceName.getParent();
      final GrMethodCallExpression callExpression = (GrMethodCallExpression) invokedExpression.getParent();
      final GrArgumentList args = callExpression.getArgumentList();
      assert args != null;
      final GrExpression arg = args.getExpressionArguments()[0];
      replaceExpression(callExpression, invokedExpression.getQualifierExpression().getText() + '[' + arg.getText() + ']');
    }
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrArgumentList args = grMethodCallExpression.getArgumentList();
      if (args == null) {
        return;
      }
      if (args.getExpressionArguments().length != 1) {
        return;
      }
      if (PsiImplUtil.hasNamedArguments(args)) {
        return;
      }
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression referenceExpression = (GrReferenceExpression) methodExpression;
      final String name = referenceExpression.getReferenceName();
      if (!"get".equals(name)) {
        return;
      }
      final GrExpression qualifier = referenceExpression.getQualifierExpression();

      if (qualifier == null || PsiUtil.isThisOrSuperRef(qualifier)) {
        return;
      }

      if (referenceExpression.getDotTokenType() == GroovyTokenTypes.mOPTIONAL_DOT) return;
      final PsiType type = qualifier.getType();
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}
