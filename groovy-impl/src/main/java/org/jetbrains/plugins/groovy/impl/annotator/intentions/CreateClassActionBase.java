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

package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.impl.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AccessToken;
import consulo.application.Application;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public abstract class CreateClassActionBase extends Intention implements SyntheticIntentionAction {
    private final GrCreateClassKind myType;

    protected final GrReferenceElement myRefElement;

    public CreateClassActionBase(GrCreateClassKind type, GrReferenceElement refElement) {
        myType = type;
        myRefElement = refElement;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        String referenceName = myRefElement.getReferenceName();
        return switch (getType()) {
            case TRAIT -> GroovyLocalize.createTrait(referenceName);
            case ENUM -> GroovyLocalize.createEnum(referenceName);
            case CLASS -> GroovyLocalize.createClassText(referenceName);
            case INTERFACE -> GroovyLocalize.createInterfaceText(referenceName);
            case ANNOTATION -> GroovyLocalize.createAnnotationText(referenceName);
            default -> LocalizeValue.empty();
        };
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return myRefElement.isValid() && ModuleUtilCore.findModuleForPsiElement(myRefElement) != null;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }


    protected GrCreateClassKind getType() {
        return myType;
    }

    @Nullable
    @RequiredReadAction
    public static GrTypeDefinition createClassByType(
        @Nonnull final PsiDirectory directory,
        @Nonnull final String name,
        @Nonnull final PsiManager manager,
        @Nullable final PsiElement contextElement,
        @Nonnull final String templateName,
        boolean allowReformatting
    ) {
        AccessToken accessToken = WriteAction.start();

        try {
            GrTypeDefinition targetClass = null;
            try {
                PsiFile file = GroovyTemplatesFactory.createFromTemplate(
                    directory,
                    name,
                    name + ".groovy",
                    templateName,
                    allowReformatting
                );
                for (PsiElement element : file.getChildren()) {
                    if (element instanceof GrTypeDefinition typeDefinition) {
                        targetClass = typeDefinition;
                        break;
                    }
                }
                if (targetClass == null) {
                    throw new IncorrectOperationException(GroovyLocalize.noClassInFileTemplate().get());
                }
            }
            catch (final IncorrectOperationException e) {
                Application.get().invokeLater(() -> Messages.showErrorDialog(
                    GroovyLocalize.cannotCreateClassErrorText(name, e.getLocalizedMessage()).get(),
                    GroovyLocalize.cannotCreateClassErrorTitle().get()
                ));
                return null;
            }
            PsiModifierList modifiers = targetClass.getModifierList();
            if (contextElement != null
                && !JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper()
                    .isAccessible(targetClass, contextElement, null)
                && modifiers != null) {
                modifiers.setModifierProperty(PsiModifier.PUBLIC, true);
            }
            return targetClass;
        }
        catch (IncorrectOperationException e) {
            throw new AssertionError(e.getMessage(), e);
        }
        finally {
            accessToken.finish();
        }
    }

    @Nullable
    @RequiredUIAccess
    protected PsiDirectory getTargetDirectory(
        @Nonnull Project project,
        @Nonnull String qualifier,
        @Nonnull String name,
        @Nullable Module module,
        @Nonnull LocalizeValue title
    ) {
        CreateClassDialog dialog = new CreateClassDialog(project, title, name, qualifier, getType(), false, module) {
            @Override
            protected boolean reportBaseInSourceSelectionInTest() {
                return true;
            }
        };
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
            return null;
        }

        return dialog.getTargetDirectory();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return element -> myRefElement.isValid();
    }
}
