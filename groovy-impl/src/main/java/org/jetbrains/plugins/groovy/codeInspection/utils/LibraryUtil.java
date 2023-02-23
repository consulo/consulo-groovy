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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import javax.annotation.Nonnull;

public class LibraryUtil {

  private LibraryUtil() {
    super();
  }

  public static boolean classIsInLibrary(@Nonnull PsiClass aClass) {
    return aClass instanceof PsiCompiledElement;
  }

  public static boolean callOnLibraryMethod(
      @Nonnull GrMethodCallExpression expression) {
    final PsiMethod method = expression.resolveMethod();
    return method != null && method instanceof PsiCompiledElement;
  }

  public static boolean isOverrideOfLibraryMethod(PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      final PsiClass containingClass =
          superMethod.getContainingClass();
      if (containingClass != null &&
          classIsInLibrary(containingClass)) {
        return true;
      }
      if (isOverrideOfLibraryMethod(superMethod)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isOverrideOfLibraryMethodParameter(
      PsiVariable variable) {
    if (variable instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter) variable;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod) scope;
        if (isOverrideOfLibraryMethod(method)) {
          return true;
        }
      }
    }
    return false;
  }
}