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
package org.jetbrains.plugins.groovy.impl.annotator.checkers;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.function.Condition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;

/**
 * @author Medvdedev Max
 */
@ExtensionImpl(order = "first")
public class AnnotationCollectorChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    return isInAliasDeclaration(annotation);
  }

  @Override
  public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    if (!isInAliasDeclaration(annotation)) {
      return false;
    }

    final PsiClass clazz = (PsiClass)annotation.getClassReference().resolve();
    if (clazz == null) {
      return true;
    }
    final GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    Pair<PsiElement, String> r = CustomAnnotationChecker.checkAnnotationArguments(clazz, attributes, false);
    if (r != null && r.getFirst() != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, r.getSecond()).range(r.getFirst()).create();
    }

    return true;
  }

  private static boolean isInAliasDeclaration(GrAnnotation annotation) {
    final PsiElement parent = annotation.getParent();
    if (parent instanceof GrModifierList) {
      final GrAnnotation collector = ContainerUtil.find(((GrModifierList)parent).getRawAnnotations(),
                                                        new Condition<GrAnnotation>() {
                                                          @Override
                                                          public boolean value(GrAnnotation annotation) {
                                                            return GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(
                                                              annotation
                                                                .getQualifiedName());
                                                          }
                                                        });
      if (collector != null) {
        return true;
      }
    }

    return false;
  }
}
