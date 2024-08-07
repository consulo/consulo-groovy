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
package org.jetbrains.plugins.groovy.impl.lang.psi.impl.auxiliary.annotation;

import com.intellij.java.language.psi.PsiAnnotationSupport;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GroovyAnnotationSupport implements PsiAnnotationSupport {
  @Override
  @Nonnull
  public GrLiteral createLiteralValue(@Nonnull String value, @Nonnull PsiElement context) {
    return (GrLiteral)GroovyPsiElementFactory.getInstance(context.getProject())
                                             .createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"");
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
