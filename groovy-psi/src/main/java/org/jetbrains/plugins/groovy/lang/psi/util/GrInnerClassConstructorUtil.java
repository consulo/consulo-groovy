/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class GrInnerClassConstructorUtil {

  @Nonnull
  public static GrParameter[] addEnclosingInstanceParam(@Nonnull GrMethod method,
                                                        @Nonnull PsiClass enclosingClass,
                                                        @Nonnull GrParameter[] originalParams,
                                                        boolean isOptional) {
    final GrParameter[] parameters = new GrParameter[originalParams.length + 1];
    final PsiClassType enclosingClassType = JavaPsiFacade.getElementFactory(method.getProject()).createType(enclosingClass, PsiSubstitutor.EMPTY);
    final GrLightParameter enclosing = new GrLightParameter("enclosing", enclosingClassType, method);
    if (isOptional) {
      enclosing.setOptional(true);
      enclosing.setInitializerGroovy(GroovyPsiElementFactory.getInstance(method.getProject()).createExpressionFromText("null"));
    }
    parameters[0] = enclosing;
    System.arraycopy(originalParams, 0, parameters, 1, originalParams.length);
    return parameters;
  }

  public static boolean isInnerClassConstructorUsedOutsideOfItParent(@Nonnull PsiMethod method, PsiElement place) {
    if (method instanceof GrMethod && method.isConstructor()) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = aClass.getContainingClass();
        if (containingClass != null &&
            PsiUtil.findEnclosingInstanceClassInScope(containingClass, place, true) == null) {
          return true;
        }
      }
    }

    return false;
  }

  @Nonnull
  public static PsiType[] addEnclosingArgIfNeeded(@Nonnull PsiType[] types, @Nonnull PsiElement place, @Nonnull PsiClass aClass) {
    if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());
        PsiClass scopeClass = PsiUtil.findEnclosingInstanceClassInScope(containingClass, place, true);
        if (scopeClass != null) {
          PsiType[] newTypes = new PsiType[types.length + 1];
          newTypes[0] = factory.createType(scopeClass);
          System.arraycopy(types, 0, newTypes, 1, types.length);
          types = newTypes;
        }
        else if (types.length == 0 || !TypesUtil.isAssignableByMethodCallConversion(factory.createType(containingClass), types[0], place)) {
          PsiType[] newTypes = new PsiType[types.length + 1];
          newTypes[0] = PsiType.NULL;
          System.arraycopy(types, 0, newTypes, 1, types.length);
          types = newTypes;
        }
      }
    }
    return types;
  }
}
