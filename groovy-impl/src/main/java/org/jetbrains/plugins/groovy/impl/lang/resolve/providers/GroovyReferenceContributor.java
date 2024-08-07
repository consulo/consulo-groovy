/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.resolve.providers;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceRegistrar;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.spock.SpockUnrollReferenceProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author Dmitry.Krasilschikov
 */
@ExtensionImpl
public class GroovyReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(psiElement(GrLiteral.class), new PropertiesReferenceProvider());

    registrar.registerReferenceProvider(GroovyPatterns.stringLiteral().withParent(GrAnnotationNameValuePair.class),
                                        new SpockUnrollReferenceProvider());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
