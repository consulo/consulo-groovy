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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import jakarta.annotation.Nonnull;

import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.util.ProcessingContext;
import consulo.language.editor.completion.CompletionProvider;

/**
 * Created by Max Medvedev on 14/05/14
 */
public class GrAnnotationAttributeCompletionProvider implements CompletionProvider
{
  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters,
                                ProcessingContext context,
                                @Nonnull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (parent instanceof GrAnnotationNameValuePair && position == ((GrAnnotationNameValuePair)parent).getNameIdentifierGroovy()) {
      GrAnnotation annotation = PsiImplUtil.getAnnotation((GrAnnotationNameValuePair)parent);
      if (annotation != null) {
        new AnnotationAttributeCompletionResultProcessor(annotation).process(result, PrefixMatcher.ALWAYS_TRUE);
      }
    }
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new GrAnnotationAttributeCompletionProvider());
  }
}
