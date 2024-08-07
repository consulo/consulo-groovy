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

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 8/15/13
 */
@ExtensionImpl
public class GrStringConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@Nonnull ApplicableTo position) {
    return position == ApplicableTo.ASSIGNMENT || position == ApplicableTo.RETURN_VALUE || position == ApplicableTo.EXPLICIT_CAST;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@Nonnull PsiType lType,
                                          @Nonnull PsiType rType,
                                          @Nonnull GroovyPsiElement context,
                                          @Nonnull ApplicableTo currentPosition) {
    if (!TypesUtil.isClassType(lType, CommonClassNames.JAVA_LANG_STRING)) return null;
    if (currentPosition == ApplicableTo.EXPLICIT_CAST) {
      return TypesUtil.isClassType(rType, GroovyCommonClassNames.GROOVY_LANG_GSTRING)
             ? ConversionResult.OK
             : null;
    }
    return ConversionResult.OK;
  }
}
