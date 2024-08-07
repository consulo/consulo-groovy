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
package org.jetbrains.plugins.groovy.impl.lang.psi.typeEnhancers;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class SignatureHintProcessor {
  private static final ExtensionPointName<SignatureHintProcessor> EP_NAME = ExtensionPointName.create(SignatureHintProcessor.class);

  static String[] buildOptions(PsiAnnotation anno) {
    PsiAnnotationMemberValue options = anno.findAttributeValue("options");
    if (options instanceof PsiLiteral) {
      Object value = ((PsiLiteral)options).getValue();
      if (value instanceof String) {
        return new String[]{(String)value};
      }
    }
    else if (options instanceof PsiArrayInitializerMemberValue) {
      PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)options).getInitializers();
      ArrayList<String> result = ContainerUtil.newArrayList();
      for (PsiAnnotationMemberValue initializer : initializers) {
        if (initializer instanceof PsiLiteral) {
          Object value = ((PsiLiteral)initializer).getValue();
          if (value instanceof String) {
            result.add((String)value);
          }
        }
      }

      return ArrayUtil.toStringArray(result);
    }

    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public abstract String getHintName();

  @Nonnull
  public abstract List<PsiType[]> inferExpectedSignatures(@Nonnull PsiMethod method,
                                                          @Nonnull PsiSubstitutor substitutor,
                                                          @Nonnull String[] options);

  @Nullable
  public static SignatureHintProcessor getHintProcessor(@Nonnull String hint) {
    for (SignatureHintProcessor processor : EP_NAME.getExtensions()) {
      if (hint.equals(processor.getHintName())) {
        return processor;
      }
    }

    return null;
  }
}
