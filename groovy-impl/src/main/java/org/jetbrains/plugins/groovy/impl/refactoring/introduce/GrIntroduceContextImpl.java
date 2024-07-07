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
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import jakarta.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceContextImpl implements GrIntroduceContext {
  private static final Logger LOG = Logger.getInstance(GrIntroduceContextImpl.class);

  private final Project myProject;
  private final Editor myEditor;
  @Nullable
  private final GrExpression myExpression;
  private final PsiElement[] myOccurrences;
  private final PsiElement myScope;
  @Nullable
  private final GrVariable myVar;
  @Nonnull
  private final PsiElement myPlace;
  private final StringPartInfo myStringPart;

  public GrIntroduceContextImpl(@Nonnull Project project,
                                Editor editor,
                                @Nullable GrExpression expression,
                                @Nullable GrVariable var,
                                @Nullable StringPartInfo stringPart,
                                @Nonnull PsiElement[] occurrences,
                                PsiElement scope) {
    myStringPart = stringPart;
    LOG.assertTrue(expression != null || var != null || stringPart != null);

    myProject = project;
    myEditor = editor;
    myExpression = expression;
    myOccurrences = occurrences;
    myScope = scope;
    myVar = var;
    myPlace = GrIntroduceHandlerBase.getCurrentPlace(expression, var, stringPart);
  }

  @Nonnull
  public Project getProject() {
    return myProject;
  }

  public Editor getEditor() {
    return myEditor;
  }

  @Nullable
  public GrExpression getExpression() {
    return myExpression;
  }

  @Nonnull
  public PsiElement[] getOccurrences() {
    return myOccurrences;
  }

  public PsiElement getScope() {
    return myScope;
  }

  @Nullable
  public GrVariable getVar() {
    return myVar;
  }

  @Nullable
  @Override
  public StringPartInfo getStringPart() {
    return myStringPart;
  }

  @Nonnull
  public PsiElement getPlace() {
    return myPlace;
  }
}
