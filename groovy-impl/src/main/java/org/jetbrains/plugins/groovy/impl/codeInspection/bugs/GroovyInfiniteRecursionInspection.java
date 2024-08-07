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

import com.intellij.java.language.psi.PsiModifier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class GroovyInfiniteRecursionInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Infinite recursion";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "<code>#ref</code> recurses infinitely, and can only complete by throwing an exception #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitMethod(@Nonnull GrMethod method) {
      super.visitMethod(method);
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!RecursionUtils.methodMayRecurse(method)) {
        return;
      }
      if (!RecursionUtils.methodDefinitelyRecurses(method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}