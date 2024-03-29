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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.java.language.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.function.Function;

/**
 * Created by Max Medvedev on 12/20/13
 */
public class GrMatcherTypeCalculator implements Function<GrBinaryFacade, PsiType> {
  public static final GrMatcherTypeCalculator INSTANCE = new GrMatcherTypeCalculator();

  @Override
  public PsiType apply(GrBinaryFacade expression) {
    return GrBinaryExpressionUtil.getTypeByFQName(GroovyCommonClassNames.JAVA_UTIL_REGEX_MATCHER, expression);
  }
}
