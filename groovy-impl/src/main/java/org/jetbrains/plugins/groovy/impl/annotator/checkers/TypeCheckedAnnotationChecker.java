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
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class TypeCheckedAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    final GrCodeReferenceElement classReference = annotation.getClassReference();
    PsiElement resolved = classReference.resolve();
    if (!(resolved instanceof PsiClass && GroovyCommonClassNames.GROOVY_TRANSFORM_TYPE_CHECKED.equals(((PsiClass)
      resolved).getQualifiedName()))) {
      return false;
    }

    String sdkVersion = GroovyConfigUtils.getInstance().getSDKVersion(annotation);
    if (!("2.1".equals(sdkVersion) || "2.1.0".equals(sdkVersion))) {
      return false;
    }

    GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    Pair<PsiElement, String> r = checkAnnotationArguments((PsiClass)resolved, attributes, false);
    if (r != null && r.getFirst() != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, r.getSecond()).range(r.getFirst()).create();
    }
    return true;
  }
}
