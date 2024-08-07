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
package org.jetbrains.plugins.groovy.impl.testIntegration;

import com.intellij.java.execution.impl.application.ApplicationRunLineMarkerProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import jakarta.annotation.Nonnull;

/**
 * @author Dmitry Avdeev
 */
@ExtensionImpl
public class GroovyAppLineMarkerContributor extends ApplicationRunLineMarkerProvider {

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  @Override
  protected boolean isIdentifier(PsiElement e) {
    return e.getNode().getElementType() == GroovyTokenTypes.mIDENT;
  }
}
