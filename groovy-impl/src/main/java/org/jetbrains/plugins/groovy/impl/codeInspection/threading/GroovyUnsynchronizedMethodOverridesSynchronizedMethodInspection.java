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
package org.jetbrains.plugins.groovy.impl.codeInspection.threading;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyUnsynchronizedMethodOverridesSynchronizedMethodInspection extends BaseInspection {

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
    return "Unsynchronized method overrides synchronized method";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Unsynchronized method '#ref' overrides a synchronized method #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        if (superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          registerMethodError(method);
          return;
        }
      }
    }
  }
}