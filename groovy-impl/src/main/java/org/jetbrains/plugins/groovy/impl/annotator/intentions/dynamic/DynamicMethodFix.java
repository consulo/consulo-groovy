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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic;

import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ui.DynamicMethodDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author Maxim.Medvedev
 */
public class DynamicMethodFix implements SyntheticIntentionAction, LowPriorityAction {
  private final GrReferenceExpression myReferenceExpression;
  private final String mySignature;

  public DynamicMethodFix(GrReferenceExpression referenceExpression, final PsiType[] argumentTypes) {
    myReferenceExpression = referenceExpression;
    mySignature = calcSignature(argumentTypes);
  }

  @Nonnull
  public String getText() {
    return GroovyBundle.message("add.dynamic.method") + mySignature;
  }

  private String calcSignature(final PsiType[] argTypes) {
    StringBuilder builder = new StringBuilder(" '").append(myReferenceExpression.getReferenceName());
    builder.append("(");

    for (int i = 0; i < argTypes.length; i++) {
      PsiType type = argTypes[i];

      if (i > 0) {
        builder.append(", ");
      }
      builder.append(type.getPresentableText());
    }
    builder.append(")");
    builder.append("' ");
    return builder.toString();
  }

  @Nonnull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile psiFile) {
    return myReferenceExpression.isValid();
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException
  {
    DynamicDialog dialog = new DynamicMethodDialog(myReferenceExpression);
    dialog.show();
  }

  public void invoke(Project project) throws IncorrectOperationException {
    final DynamicElementSettings settings = QuickfixUtil.createSettings(myReferenceExpression);
    DynamicManager.getInstance(project).addMethod(settings);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
