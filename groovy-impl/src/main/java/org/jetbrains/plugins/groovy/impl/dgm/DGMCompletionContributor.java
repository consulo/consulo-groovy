/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.dgm;

import com.intellij.java.impl.codeInsight.completion.AllClassesGetter;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.impl.lang.completion.GroovyCompletionUtil;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class DGMCompletionContributor extends CompletionContributor {
  public DGMCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(PropertiesTokenTypes.KEY_CHARACTERS),
           new CompletionProvider() {
             @Override
             public void addCompletions(@Nonnull CompletionParameters parameters,
                                        ProcessingContext context,
                                        @Nonnull CompletionResultSet result) {
               PsiElement position = parameters.getPosition();
               if (!DGMUtil.isInDGMFile(position)) return;

               Map<String, String> map = ((PropertiesFile)position.getContainingFile()).getNamesMap();
               for (String key : DGMUtil.KEYS) {
                 if (!map.containsKey(key)) {
                   result.addElement(LookupElementBuilder.create(key));
                 }
               }
             }
           });

    extend(CompletionType.BASIC, PlatformPatterns.psiElement(PropertiesTokenTypes.VALUE_CHARACTERS),
           new CompletionProvider() {
             @Override
             public void addCompletions(@Nonnull CompletionParameters parameters,
                                        ProcessingContext context,
                                        @Nonnull final CompletionResultSet result) {
               PsiElement position = parameters.getPosition();
               if (!DGMUtil.isInDGMFile(position)) return;

               AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher(), true,
                                                   aClass -> result.addElement(GroovyCompletionUtil.createClassLookupItem(aClass)));
             }
           });
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PropertiesLanguage.INSTANCE;
  }
}
