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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesProcessor;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Max Medvedev
 */
public class GrMoveToDirFix implements LocalQuickFix {
    private String myPackageName;

    public GrMoveToDirFix(String packageName) {
        myPackageName = packageName;
    }

    @Nonnull
    @Override
    public String getName() {
        String packName = StringUtil.isEmptyOrSpaces(myPackageName) ? "default package" : myPackageName;
        return GroovyIntentionsBundle.message("move.to.correct.dir", packName);
    }

    @Nonnull
    @Override
    public String getFamilyName() {
        return GroovyIntentionsBundle.message("move.to.correct.dir.family.name");
    }

    @Override
    @RequiredUIAccess
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiFile file = descriptor.getPsiElement().getContainingFile();

        if (!(file instanceof GroovyFile)) {
            return;
        }

        VirtualFile vfile = file.getVirtualFile();
        if (vfile == null) {
            return;
        }

        final Module module = ModuleUtilCore.findModuleForFile(vfile, project);
        if (module == null) {
            return;
        }

        final String packageName = ((GroovyFile)file).getPackageName();
        PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, null, true);
        if (directory == null) {
            return;
        }

        String error = RefactoringMessageUtil.checkCanCreateFile(directory, file.getName());
        if (error != null) {
            Messages.showMessageDialog(project, error, CommonLocalize.titleError().get(), UIUtil.getErrorIcon());
            return;
        }
        new MoveFilesOrDirectoriesProcessor(
            project,
            new PsiElement[]{file},
            directory,
            false,
            false,
            false,
            null,
            null
        ).run();
    }
}
