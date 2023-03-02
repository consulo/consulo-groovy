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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public interface GrIntroduceContext {
  @Nonnull
  Project getProject();

  Editor getEditor();

  @Nullable
  GrExpression getExpression();

  @Nullable
  GrVariable getVar();

  @Nullable
  StringPartInfo getStringPart();

  @Nonnull
  PsiElement[] getOccurrences();

  PsiElement getScope();

  @Nonnull
  PsiElement getPlace();
}
