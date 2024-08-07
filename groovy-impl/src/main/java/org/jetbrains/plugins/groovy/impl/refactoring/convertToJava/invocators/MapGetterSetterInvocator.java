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
package org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.invocators;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.ExpressionGenerator;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.GenerationUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class MapGetterSetterInvocator extends CustomMethodInvocator {
  @Override
  protected boolean invoke(@Nonnull ExpressionGenerator generator,
                           @Nonnull PsiMethod method,
                           @Nullable GrExpression caller,
                           @Nonnull GrExpression[] exprs,
                           @Nonnull GrNamedArgument[] namedArgs,
                           @Nonnull GrClosableBlock[] closures,
                           @Nonnull PsiSubstitutor substitutor,
                           @Nonnull GroovyPsiElement context) {
    if (!method.getName().equals("putAt") && !method.getName().equals("getAt")) return false;

    final PsiClass clazz = method.getContainingClass();
    if (clazz == null) return false;

    final String qname = clazz.getQualifiedName();
    if (!GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(qname)) return false;


    if (caller == null) return false;
    final PsiType type = caller.getType();

    if (method.getName().equals("getAt")) {
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        GenerationUtil.invokeMethodByName(caller, "get", exprs, namedArgs, closures, generator, context);
        return true;
      }
      else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_LIST)) {
        GenerationUtil.invokeMethodByName(caller, "get", exprs, namedArgs, closures, generator, context);
        return true;
      }
    }
    else if (method.getName().equals("putAt")) {
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        GenerationUtil.invokeMethodByName(caller, "put", exprs, namedArgs, closures, generator, context);
        return true;
      }
      else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_LIST)) {
        GenerationUtil.invokeMethodByName(caller, "set", exprs, namedArgs, closures, generator, context);
        return true;
      }
    }


    return false;
  }
}
