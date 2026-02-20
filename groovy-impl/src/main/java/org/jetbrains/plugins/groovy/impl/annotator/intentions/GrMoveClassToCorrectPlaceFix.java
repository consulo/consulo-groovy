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

import consulo.codeEditor.Editor;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Max Medvedev
 */
public class GrMoveClassToCorrectPlaceFix implements SyntheticIntentionAction {
    private final GrTypeDefinition myClass;

    public GrMoveClassToCorrectPlaceFix(GrTypeDefinition clazz) {
        myClass = clazz;
        assert !myClass.isAnonymous();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyLocalize.moveClass0FromMethod(myClass.getName());
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return myClass.isValid();
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(myClass, GrTypeDefinition.class);
        if (containingClass != null) {
            containingClass.add(myClass);
        }
        else {
            PsiFile containingFile = myClass.getContainingFile();
            PsiElement added = containingFile.add(myClass);
            PsiElement prevSibling = added.getPrevSibling();
            if (prevSibling != null && prevSibling.getNode().getElementType() != GroovyTokenTypes.mNLS) {
                containingFile.getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", added.getNode());
            }
        }

        myClass.delete();
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
