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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.java.language.psi.PsiMember;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
public interface GrReferenceExpression extends GrExpression, GrReferenceElement<GrExpression> {

  @Nullable
  GrExpression getQualifierExpression();

  @Nullable
  IElementType getDotTokenType();

  @Nullable
  PsiElement getDotToken();

  boolean hasAt();

  boolean hasMemberPointer();

  void replaceDotToken(PsiElement newDotToken);

  //not caching!
  @Nonnull
  GroovyResolveResult[] getSameNameVariants();

  GrReferenceExpression bindToElementViaStaticImport(@Nonnull PsiMember member);

  GroovyResolveResult[] resolveByShape();
}
