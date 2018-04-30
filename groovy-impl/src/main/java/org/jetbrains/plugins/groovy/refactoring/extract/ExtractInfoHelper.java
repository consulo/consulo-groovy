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
package org.jetbrains.plugins.groovy.refactoring.extract;

import javax.annotation.Nonnull;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

/**
 * @author Max Medvedev
 */
public interface ExtractInfoHelper {
  @Nonnull
  Project getProject();

  @Nonnull
  ParameterInfo[] getParameterInfos();

  @Nonnull
  VariableInfo[] getOutputVariableInfos();

  @Nonnull
  String[] getArgumentNames();

  @Nonnull
  PsiType getOutputType();

  @Nonnull
  PsiElement[] getInnerElements();

  @Nonnull
  GrStatement[] getStatements();

  @javax.annotation.Nullable
  StringPartInfo getStringPartInfo();

  boolean hasReturnValue();

  String getName();

  PsiElement getContext();

  boolean isForceReturn();
}
