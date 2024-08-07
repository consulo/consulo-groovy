/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl(order = "last")
public class GroovyMethodReturnNamedArgumentProvider extends GroovyNamedArgumentProvider {
  @Override
  public void getNamedArguments(@Nonnull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, NamedArgumentDescriptor> result) {
    if (!forCompletion || !(resolve instanceof PsiMethod)) return;

    PsiType returnType = ((PsiMethod)resolve).getReturnType();
    if (!(returnType instanceof PsiClassType)) return;

    Map<String, NamedArgumentDescriptor> map = new HashMap<String, NamedArgumentDescriptor>();

    GroovyConstructorNamedArgumentProvider.processClass(call, (PsiClassType)returnType, argumentName, map);

    for (String name : map.keySet()) {
      result.put(name, NamedArgumentDescriptor.SIMPLE_UNLIKELY);
    }
  }
}
