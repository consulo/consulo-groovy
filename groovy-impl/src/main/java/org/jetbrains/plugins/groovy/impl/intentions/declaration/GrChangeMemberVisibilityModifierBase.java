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
package org.jetbrains.plugins.groovy.impl.intentions.declaration;

import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author Max Medvedev
 */
public abstract class GrChangeMemberVisibilityModifierBase extends Intention {
    private final String myModifier;
    @Nonnull
    private final LocalizeValue myText;

    public GrChangeMemberVisibilityModifierBase(String modifier, @Nonnull LocalizeValue text) {
        myModifier = modifier;
        myText = text;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Override
    @RequiredWriteAction
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        if (!(element.getParent() instanceof GrMember member)) {
            return;
        }

        member.getModifierList().setModifierProperty(myModifier, true);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return element -> element.getParent() instanceof GrMember member
            && member instanceof GrNamedElement namedElem
            && (namedElem.getNameIdentifierGroovy() == element || member.getModifierList() == element)
            && member.getModifierList() instanceof GrModifierList modifierList
            && !modifierList.hasExplicitModifier(myModifier);
    }
}
