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
package org.jetbrains.plugins.groovy.impl.compiler;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.DumbAware;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.ui.ex.action.coroutine.ActionSafeReadLock;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.virtualFileSystem.VirtualFile;
import consulo.compiler.setting.ExcludeEntryDescription;
import consulo.ide.setting.ShowSettingsUtil;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author peter
 */
public class ExcludeFromStubGenerationAction extends AnAction implements DumbAware, AnActionWithAsyncUpdate {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        PsiFile file = e.getData(LangDataKeys.PSI_FILE);

        assert file != null && file.getLanguage() == GroovyLanguage.INSTANCE;

        doExcludeFromStubGeneration(file);
    }

    @RequiredUIAccess
    public static void doExcludeFromStubGeneration(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        assert virtualFile != null;
        Project project = file.getProject();

        ShowSettingsUtil.getInstance().showAndSelect(
            project,
            GroovyCompilerConfigurable.class,
            configurable -> configurable.getExcludes().addEntry(new ExcludeEntryDescription(virtualFile, false, true, project))
        );
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        return ActionSafeReadLock.run(e, presentation -> e.getPresentation().setEnabledAndVisible(isEnabled(e))).toCoroutine();
    }

    @RequiredReadAction
    private static boolean isEnabled(AnActionEvent e) {
        PsiFile file = e.getData(LangDataKeys.PSI_FILE);
        if (file == null || file.getLanguage() != GroovyLanguage.INSTANCE) {
            return false;
        }

        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null && !GroovyCompilerConfiguration.getExcludeConfiguration(file.getProject()).isExcluded(virtualFile);
    }
}
