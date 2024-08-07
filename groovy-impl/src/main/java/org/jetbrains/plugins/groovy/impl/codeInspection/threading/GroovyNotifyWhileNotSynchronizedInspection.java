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

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;

public class GroovyNotifyWhileNotSynchronizedInspection extends BaseInspection {
  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return THREADING_ISSUES;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "'notify()' or 'notifyAll()' while not synced";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to'#ref' outside of synchronized context #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression reference = (GrReferenceExpression) methodExpression;
      final String name = reference.getReferenceName();
      if (!"notify".equals(name) && !"notifyAll".equals(name)) {
        return;
      }
      final PsiMethod method = grMethodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        return;
      }
      final GrMethod containingMethod = PsiTreeUtil.getParentOfType(grMethodCallExpression, GrMethod.class);
      if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      final GrStatement parent = PsiTreeUtil.getParentOfType(grMethodCallExpression, GrSynchronizedStatement.class, GrClosableBlock.class);
      if (parent instanceof GrSynchronizedStatement) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}