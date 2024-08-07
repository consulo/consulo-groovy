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
package org.jetbrains.plugins.groovy.impl.actions;

import jakarta.annotation.Nonnull;

import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.ConvertToJavaHandler;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaAction extends BaseRefactoringAction
{

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@Nonnull PsiElement element, @Nonnull Editor editor, @Nonnull PsiFile file, @Nonnull DataContext context) {
    return isEnabledOnElements(new PsiElement[]{element});
  }

  @Override
  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return super.isEnabledOnDataContext(dataContext);
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return GroovyFileType.GROOVY_LANGUAGE == language;
  }

  @Override
  protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {

    for (PsiElement element : elements) {
        final PsiFile containingFile = element.getContainingFile();
        if (containingFile instanceof GroovyFile) continue;
        return false;
    }
    return true;
  }

  @Override
  protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
    return new ConvertToJavaHandler();
  }
}
