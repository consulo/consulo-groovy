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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable;

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.NameValidator;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * @author Max Medvedev
 */
public class GrVariableNameSuggester extends LinkedHashSet<String> {
  private final GrIntroduceContext myContext;
  private final NameValidator myValidator;

  public GrVariableNameSuggester(GrIntroduceContext context, NameValidator validator) {
    myContext = context;
    myValidator = validator;
  }

  @Nonnull
  public LinkedHashSet<String> suggestNames() {
    GrExpression expression = myContext.getExpression() != null ? myContext.getExpression() : myContext.getStringPart().getLiteral();
    return new LinkedHashSet<String>(Arrays.asList(GroovyNameSuggestionUtil.suggestVariableNames(expression, myValidator)));
  }
}
