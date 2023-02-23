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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.java.language.psi.PsiClass;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class DynamicPropertyFix extends GroovyFix implements IntentionAction, LowPriorityAction {
  private final GrReferenceExpression myReferenceExpression;
  private final GrArgumentLabel myArgumentLabel;
  private final PsiClass myTargetClass;

  public DynamicPropertyFix(GrReferenceExpression referenceExpression) {
    myReferenceExpression = referenceExpression;
    myArgumentLabel = null;
    myTargetClass = null;
  }

  public DynamicPropertyFix(GrArgumentLabel argumentLabel, PsiClass targetClass) {
    myArgumentLabel = argumentLabel;
    myReferenceExpression = null;
    myTargetClass = targetClass;
  }

  @Nonnull
  public String getText() {
    return GroovyBundle.message("add.dynamic.property", getRefName());
  }


  @Nonnull
  @Override
  public String getName() {
    return getText();
  }

  @Nullable
  private String getRefName() {
    if (myReferenceExpression != null) {
      return myReferenceExpression.getReferenceName();
    }
    else {
      return myArgumentLabel.getName();
    }
  }

  @Nonnull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile psiFile) {
    return (myReferenceExpression == null || myReferenceExpression.isValid()) && (myArgumentLabel == null || myArgumentLabel.isValid());
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    invokeInner();
  }

  private void invokeInner() {
    DynamicDialog dialog;
    if (myReferenceExpression != null) {
      dialog = new DynamicPropertyDialog(myReferenceExpression);
    }
    else {
      dialog = new DynamicPropertyDialog(myArgumentLabel, myTargetClass);
    }
    dialog.show();
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
  {
    invokeInner();
  }

  /**
   * for tests
   */
  public void invoke(Project project) throws IncorrectOperationException {
    final DynamicElementSettings settings;
    if (myReferenceExpression != null) {
      settings = QuickfixUtil.createSettings(myReferenceExpression);
    }
    else {
      settings = QuickfixUtil.createSettings(myArgumentLabel, myTargetClass);
    }
    DynamicManager.getInstance(project).addProperty(settings);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
