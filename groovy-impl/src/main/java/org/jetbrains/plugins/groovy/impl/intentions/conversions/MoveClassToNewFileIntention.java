/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Maxim.Medvedev
 */
public class MoveClassToNewFileIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.moveClassToNewFileIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrTypeDefinition psiClass = (GrTypeDefinition) element.getParent();
        String name = psiClass.getName();

        PsiFile file = psiClass.getContainingFile();
        String fileExtension = FileUtil.getExtension(file.getName());
        String newFileName = name + "." + fileExtension;
        PsiDirectory dir = file.getParent();
        if (dir != null) {
            if (dir.findFile(newFileName) != null) {
                if (!ApplicationManager.getApplication().isUnitTestMode()) {
                    LocalizeValue message = GroovyIntentionLocalize.fileExists(newFileName, dir.getName());
                    CommonRefactoringUtil.showErrorHint(project, editor, message.get(), getText().get(), null);
                }
                return;
            }
        }

        GroovyFile newFile =
            (GroovyFile) GroovyTemplatesFactory.createFromTemplate(dir, name, newFileName, GroovyTemplates.GROOVY_CLASS, true);
        GrTypeDefinition template = newFile.getTypeDefinitions()[0];
        PsiElement newClass = template.replace(psiClass);
        GrDocComment docComment = psiClass.getDocComment();
        if (newClass instanceof GrTypeDefinition && docComment != null) {
            GrDocComment newDoc = ((GrTypeDefinition) newClass).getDocComment();
            if (newDoc != null) {
                newDoc.replace(docComment);
            }
            else {
                PsiElement parent = newClass.getParent();
                parent.addBefore(docComment, psiClass);
                parent.getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", psiClass.getNode());
            }
            docComment.delete();
        }
        psiClass.delete();
        IntentionUtils.positionCursor(project, newClass.getContainingFile(), newClass.getNavigationElement());
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ClassNameDiffersFromFileNamePredicate(null, true);
    }
}
