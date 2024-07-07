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

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Created by Max Medvedev on 14/05/14
 */
public class AnnotationAttributeCompletionResultProcessor {
  private final GrAnnotation myAnnotation;

  public AnnotationAttributeCompletionResultProcessor(@Nonnull GrAnnotation annotation) {
    myAnnotation = annotation;
  }

  public void process(@Nonnull Consumer<LookupElement> consumer, @Nonnull PrefixMatcher matcher) {
    GrCodeReferenceElement ref = myAnnotation.getClassReference();
    PsiElement resolved = ref.resolve();

    if (resolved instanceof PsiClass) {
      final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector((PsiClass)resolved);

      if (annotationCollector != null) {
        final ArrayList<GrAnnotation> annotations = ContainerUtil.newArrayList();
        GrAnnotationCollector.collectAnnotations(annotations, myAnnotation, annotationCollector);

        Set<String> usedNames = new HashSet<>();
        for (GrAnnotation annotation : annotations) {
          final PsiElement resolvedAliased = annotation.getClassReference().resolve();
          if (resolvedAliased instanceof PsiClass && ((PsiClass)resolvedAliased).isAnnotationType()) {
            for (PsiMethod method : ((PsiClass)resolvedAliased).getMethods()) {
              if (usedNames.add(method.getName())) {
                for (LookupElement element : GroovyCompletionUtil
                  .createLookupElements(new GroovyResolveResultImpl(method, true), false, matcher, null)) {
                  consumer.accept(element);
                }
              }
            }
          }
        }
      }
      else if (((PsiClass)resolved).isAnnotationType()) {
        for (PsiMethod method : ((PsiClass)resolved).getMethods()) {
          for (LookupElement element : GroovyCompletionUtil
            .createLookupElements(new GroovyResolveResultImpl(method, true), false, matcher, null)) {
            consumer.accept(element);
          }
        }
      }
    }
  }

}
