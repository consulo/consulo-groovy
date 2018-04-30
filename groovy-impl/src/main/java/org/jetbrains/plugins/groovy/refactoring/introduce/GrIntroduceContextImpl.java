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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import javax.annotation.Nonnull;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceContextImpl implements GrIntroduceContext {
  private static final Logger LOG = Logger.getInstance(GrIntroduceContextImpl.class);

  private final Project myProject;
  private final Editor myEditor;
  @Nullable private final GrExpression myExpression;
  private final PsiElement[] myOccurrences;
  private final PsiElement myScope;
  @Nullable private final GrVariable myVar;
  @Nonnull
  private final PsiElement myPlace;
  private final StringPartInfo myStringPart;

  public GrIntroduceContextImpl(@Nonnull Project project,
                                Editor editor,
                                @javax.annotation.Nullable GrExpression expression,
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

  @javax.annotation.Nullable
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

  @javax.annotation.Nullable
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
