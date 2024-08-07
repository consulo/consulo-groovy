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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by Max Medvedev on 28/02/14
 */
public class SimpleTypeHintProcessor extends SignatureHintProcessor {
  @Override
  public String getHintName() {
    return "groovy.transform.stc.SimpleType";
  }

  @Nonnull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@Nonnull final PsiMethod method,
                                                 @Nonnull PsiSubstitutor substitutor,
                                                 @Nonnull String[] options) {
    return Collections.singletonList(ContainerUtil.map(options, new Function<String, PsiType>() {
      @Override
      public PsiType apply(String value) {
        try {
          PsiType type = JavaPsiFacade.getElementFactory(method.getProject()).createTypeFromText(value, method);
          return DefaultGroovyMethods.asBoolean(type) ? type : PsiType.NULL;
        }
        catch (IncorrectOperationException e) {
          return PsiType.NULL;
        }
      }
    }, new PsiType[options.length]));
  }
}
