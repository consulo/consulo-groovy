/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.application.HelpManager;
import consulo.ide.impl.idea.refactoring.inline.InlineOptionsDialog;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.project.Project;

/**
* @author Max Medvedev
*/
class InlineGroovyFieldDialog extends InlineOptionsDialog {

  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");

  private final PsiField myField;

  public InlineGroovyFieldDialog(Project project, PsiField field, boolean invokedOnReference) {
    super(project, true, field);
    myField = field;
    myInvokedOnReference = invokedOnReference;

    setTitle(REFACTORING_NAME);

    init();
  }

  protected String getNameLabelText() {
    @SuppressWarnings("StaticFieldReferencedViaSubclass")
    String fieldText = PsiFormatUtil.formatVariable(myField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
    return RefactoringBundle.message("inline.field.field.name.label", fieldText);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.field.border.title");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  protected String getInlineAllText() {
    return RefactoringBundle.message("all.references.and.remove.the.field");
  }

  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_FIELD_THIS;
  }

  protected void doAction() {
    if (getOKAction().isEnabled()) {
      JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
      if (myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
        settings.INLINE_FIELD_THIS = isInlineThisOnly();
      }
      close(OK_EXIT_CODE);
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_FIELD);
  }
}
