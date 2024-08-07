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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.annotation.AnnotationHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class NewifyAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    return GroovyCommonClassNames.GROOVY_LANG_NEWIFY.equals(annotation.getQualifiedName()) && annotation
      .getParameterList().getAttributes().length == 0;
  }
}
