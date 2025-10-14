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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.editor.refactoring.RefactoringFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.function.Consumer;

/**
 * @author Maxim.Medvedev
 */
public class RenameFileWithClassIntention extends Intention implements Consumer<GrTypeDefinition> {
    private String myNewFileName = null;

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        final PsiFile file = element.getContainingFile();
        RefactoringFactory.getInstance(project).createRename(file, myNewFileName, true, true).run();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.renameFileTo0(myNewFileName);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ClassNameDiffersFromFileNamePredicate(this);
    }

    @Override
    public void accept(GrTypeDefinition def) {
        final String name = def.getName();
        final PsiFile file = def.getContainingFile();
        myNewFileName = name + "." + FileUtil.getExtension(file.getName());
    }
}
