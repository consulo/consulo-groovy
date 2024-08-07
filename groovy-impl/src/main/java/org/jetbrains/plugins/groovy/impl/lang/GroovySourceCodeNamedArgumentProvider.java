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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrNamedArgumentSearchVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * @author user
 */
@ExtensionImpl(order = "first")
public class GroovySourceCodeNamedArgumentProvider extends GroovyNamedArgumentProvider {
  @Override
  public void getNamedArguments(@Nonnull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, NamedArgumentDescriptor> result) {
    if (resolve instanceof GrMethod) {
      result.putAll(((GrMethod)resolve).getNamedParameters());
    }
    else if (resolve instanceof GrField) {
      result.putAll(((GrField)resolve).getNamedParameters());
    }
    else if (resolve instanceof GrVariable) {
      result.putAll(GrNamedArgumentSearchVisitor.find((GrVariable)resolve));
    }
  }
}
