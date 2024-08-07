/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.surroundWith;

import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nonnull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.07.2007
 */
public abstract class GroovyConditionSurrounder extends GroovyExpressionSurrounder {
  protected boolean isApplicable(@Nonnull PsiElement element) {
    if (!GroovyManyStatementsSurrounder.isStatement(element) || !(element instanceof GrExpression)) return false;

    PsiType type = ((GrExpression)element).getType();
    return PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type));
  }
}
