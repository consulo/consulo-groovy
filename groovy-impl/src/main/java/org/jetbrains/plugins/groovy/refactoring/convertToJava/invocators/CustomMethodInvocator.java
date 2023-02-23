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
package org.jetbrains.plugins.groovy.refactoring.convertToJava.invocators;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.component.extension.ExtensionPointName;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.ExpressionGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public abstract class CustomMethodInvocator {
  private static final ExtensionPointName<CustomMethodInvocator> EP_NAME = ExtensionPointName.create(CustomMethodInvocator.class);

  protected abstract boolean invoke(@Nonnull ExpressionGenerator generator,
                                    @Nonnull PsiMethod method,
                                    @Nullable GrExpression caller,
                                    @Nonnull GrExpression[] exprs,
                                    @Nonnull GrNamedArgument[] namedArgs,
                                    @Nonnull GrClosableBlock[] closures,
                                    @Nonnull PsiSubstitutor substitutor,
                                    @Nonnull GroovyPsiElement context);

  public static boolean invokeMethodOn(@Nonnull ExpressionGenerator generator,
                                       @Nonnull GrGdkMethod method,
                                       @Nullable GrExpression caller,
                                       @Nonnull GrExpression[] exprs,
                                       @Nonnull GrNamedArgument[] namedArgs,
                                       @Nonnull GrClosableBlock[] closures,
                                       @Nonnull PsiSubstitutor substitutor,
                                       @Nonnull GroovyPsiElement context) {
    final PsiMethod staticMethod = method.getStaticMethod();
    for (CustomMethodInvocator invocator : EP_NAME.getExtensions()) {
      if (invocator.invoke(generator, staticMethod, caller, exprs, namedArgs, closures, substitutor, context)) {
        return true;
      }
    }

    return false;
  }
}
