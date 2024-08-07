/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.psi.typeEnhancers;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GrClassConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@Nonnull ApplicableTo position) {
    switch (position) {
      case ASSIGNMENT:
      case RETURN_VALUE:
        return true;
      default:
        return false;
    }
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@Nonnull PsiType targetType,
                                          @Nonnull PsiType actualType,
                                          @Nonnull GroovyPsiElement context,
                                          @Nonnull ApplicableTo currentPosition) {
    if (!(targetType instanceof PsiClassType) ||
        !((PsiClassType)targetType).rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS)) {
      return null;
    }
    if (actualType == PsiType.NULL) return ConversionResult.OK;
    final GrLiteral literal = getLiteral(context);
    final Object value = literal == null ? null : literal.getValue();
    final String fqn = value == null ? null : value.toString();
    final PsiClass psiClass = fqn == null ? null : JavaPsiFacade.getInstance(context.getProject()).findClass(
      fqn, context.getResolveScope()
    );
    return psiClass == null ? ConversionResult.WARNING : ConversionResult.OK;
  }
}
