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
package org.jetbrains.plugins.groovy.impl.actions.generate;

import com.intellij.java.impl.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Dmitry.Krasilschikov
 * @since 2008-05-21
 */
public abstract class GrBaseGenerateAction extends BaseGenerateAction {
    protected GrBaseGenerateAction(CodeInsightActionHandler handler, @Nonnull LocalizeValue text, @Nonnull LocalizeValue description) {
        super(handler, text);
        getTemplatePresentation().setDescriptionValue(description);
    }

    @Override
    @RequiredReadAction
    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        if (file instanceof PsiCompiledElement) {
            return false;
        }
        if (!GroovyFileType.INSTANCE.equals(file.getFileType())) {
            return false;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        PsiClass targetClass = getTargetClass(editor, file);
        if (targetClass == null) {
            return false;
        }
        if (targetClass.isInterface()) {
            return false; //?
        }

        return true;
    }
}
