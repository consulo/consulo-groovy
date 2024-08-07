/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import jakarta.annotation.Nonnull;

import consulo.language.pattern.PatternCondition;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

public class GrAnnotationPattern extends GroovyElementPattern<GrAnnotation, GrAnnotationPattern> {
  public GrAnnotationPattern() {
    super(GrAnnotation.class);
  }

  @Nonnull
  public static GrAnnotationPattern annotation() {
    return new GrAnnotationPattern();
  }

  @Nonnull
  public GrAnnotationPattern withQualifiedName(@Nonnull final String qname) {
    return with(new PatternCondition<GrAnnotation>("withQualifiedName") {
      @Override
      public boolean accepts(@Nonnull GrAnnotation annotation, ProcessingContext context) {
        return qname.equals(annotation.getQualifiedName());
      }
    });
  }
}
