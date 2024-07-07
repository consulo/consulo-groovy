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
package org.jetbrains.plugins.groovy.impl.dsl;

import consulo.codeEditor.Editor;
import consulo.execution.unscramble.UnscrambleService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import jakarta.annotation.Nonnull;

public class InvestigateFix implements SyntheticIntentionAction {
  private final String myReason;

  public InvestigateFix(String reason) {
    myReason = reason;
  }

  @RequiredUIAccess
  static void analyzeStackTrace(Project project, String exceptionText) {
    UnscrambleService unscrambleService = project.getInstance(UnscrambleService.class);
    unscrambleService.showAsync(exceptionText);
  }

  @Nonnull
  @Override
  public String getText() {
    return "View details";
  }

  @Nonnull
  //@Override
  public String getFamilyName() {
    return "Investigate DSL descriptor processing error";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    analyzeStackTrace(project, myReason);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
