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

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrBooleanTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@Nonnull ApplicableTo position) {
    return true;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@Nonnull PsiType targetType,
                                          @Nonnull PsiType actualType,
                                          @Nonnull GroovyPsiElement context,
                                          @Nonnull ApplicableTo currentPosition) {
    if (PsiType.BOOLEAN != TypesUtil.unboxPrimitiveTypeWrapper(targetType)) return null;
    if (PsiType.NULL == actualType) {
      switch (currentPosition) {
        case METHOD_PARAMETER:
          return null;
        case EXPLICIT_CAST:
        case ASSIGNMENT:
        case RETURN_VALUE:
          return ConversionResult.OK;
        default:
          return null;
      }
    }
    return currentPosition == ApplicableTo.ASSIGNMENT || currentPosition == ApplicableTo.RETURN_VALUE
           ? ConversionResult.OK
           : null;
  }
}
