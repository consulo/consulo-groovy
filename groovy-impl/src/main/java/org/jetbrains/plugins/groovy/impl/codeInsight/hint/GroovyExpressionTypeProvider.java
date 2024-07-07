/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInsight.hint;

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.ExpressionTypeProvider;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;

import jakarta.annotation.Nonnull;
import java.util.List;

@ExtensionImpl
public class GroovyExpressionTypeProvider extends ExpressionTypeProvider<GrExpression> {

  @Nonnull
  @Override
  public String getInformationHint(@Nonnull GrExpression element) {
    final PsiType type = element.getType();
    return type == null ? "<unknown>" : StringUtil.escapeXml(type.getCanonicalText());
  }

  @Nonnull
  @Override
  public String getErrorHint() {
    return "No expression found";
  }

  @Nonnull
  @Override
  public List<GrExpression> getExpressionsAt(@Nonnull PsiElement elementAt) {
    return GrIntroduceHandlerBase.collectExpressions(elementAt, true);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
