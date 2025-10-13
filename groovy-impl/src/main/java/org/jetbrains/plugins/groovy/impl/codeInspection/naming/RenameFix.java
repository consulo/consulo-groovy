/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.naming;

import consulo.application.ApplicationManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.RefactoringFactory;
import consulo.language.editor.refactoring.RenameRefactoring;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandlerFactory;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;

public class RenameFix extends GroovyFix {
    private final String targetName;

    public RenameFix() {
        super();
        targetName = null;
    }

    public RenameFix(String targetName) {
        super();
        this.targetName = targetName;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        if (targetName == null) {
            return LocalizeValue.localizeTODO("Rename");
        }
        else {
            return LocalizeValue.localizeTODO("Rename to " + targetName);
        }
    }

    @Override
    public void doFix(final Project project, ProblemDescriptor descriptor) {
        final PsiElement nameIdentifier = descriptor.getPsiElement();
        final PsiElement elementToRename = nameIdentifier.getParent();
        if (targetName == null) {
            final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler renameHandler =
                factory.createRenameHandler();
            final DataManager dataManager = DataManager.getInstance();
            final DataContext dataContext = dataManager.getDataContext();
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    renameHandler.invoke(project, new PsiElement[]{elementToRename},
                        dataContext
                    );
                }
            };
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                runnable.run();
            }
            else {
                ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
            }
        }
        else {
            final RefactoringFactory factory =
                RefactoringFactory.getInstance(project);
            final RenameRefactoring renameRefactoring =
                factory.createRename(elementToRename, targetName);
            renameRefactoring.run();
        }
    }
}