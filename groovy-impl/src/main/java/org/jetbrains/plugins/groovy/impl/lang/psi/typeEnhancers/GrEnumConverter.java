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
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isEnum;

@ExtensionImpl
public class GrEnumConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@Nonnull ApplicableTo position) {
    return position == ApplicableTo.ASSIGNMENT || position == ApplicableTo.RETURN_VALUE;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@Nonnull PsiType targetType,
                                          @Nonnull PsiType actualType,
                                          @Nonnull GroovyPsiElement context,
                                          @Nonnull ApplicableTo currentPosition) {
    if (!isEnum(targetType)) return null;
    if (InheritanceUtil.isInheritor(actualType, GroovyCommonClassNames.GROOVY_LANG_GSTRING) ||
        InheritanceUtil.isInheritor(actualType, CommonClassNames.JAVA_LANG_STRING)) {
      return GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY1_8)
             ? ConversionResult.OK
             : ConversionResult.ERROR;
    }
    return null;
  }
}
