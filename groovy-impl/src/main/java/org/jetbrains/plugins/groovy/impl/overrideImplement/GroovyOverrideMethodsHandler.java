/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.overrideImplement;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.Language;
import consulo.language.editor.generation.OverrideMethodHandler;
import consulo.language.editor.hint.HintManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import jakarta.annotation.Nonnull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 11.09.2007
 */
@ExtensionImpl
public class GroovyOverrideMethodsHandler implements OverrideMethodHandler {
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  public void invoke(@Nonnull final Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass == null) return;

    if (OverrideImplementUtil.getMethodSignaturesToOverride(aClass).isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "No methods to override have been found");
      return;
    }

    OverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
