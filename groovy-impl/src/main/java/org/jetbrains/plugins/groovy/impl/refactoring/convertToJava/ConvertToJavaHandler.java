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
package org.jetbrains.plugins.groovy.impl.refactoring.convertToJava;

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaHandler implements RefactoringActionHandler {
  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("convert.to.java.refactoring.name");

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invokeInner(project, new PsiElement[]{file}, editor);
  }

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    Editor editor = null;
    if (dataContext != null) {
      editor = dataContext.getData(PlatformDataKeys.EDITOR);
    }
    invokeInner(project, elements, editor);
  }

  private static void invokeInner(Project project, PsiElement[] elements, Editor editor) {
    Set<GroovyFile> files = new HashSet<>();

    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile)) {
        element = element.getContainingFile();
      }

      if (element instanceof GroovyFile) {
        files.add((GroovyFile)element);
      }
      else {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          CommonRefactoringUtil.showErrorHint(project,
                                              editor,
                                              GroovyRefactoringBundle.message("convert.to.java.can.work.only.with.groovy"),
                                              REFACTORING_NAME,
                                              null);
          return;
        }
      }
    }

    new ConvertToJavaProcessor(project, files.toArray(new GroovyFile[files.size()])).run();
  }
}
