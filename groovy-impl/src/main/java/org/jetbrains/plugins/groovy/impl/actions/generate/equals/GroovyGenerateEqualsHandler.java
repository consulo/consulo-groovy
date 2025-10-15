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
package org.jetbrains.plugins.groovy.impl.actions.generate.equals;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersHandlerBase;
import com.intellij.java.impl.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.impl.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.groovy.impl.localize.GroovyCodeInsightLocalize;
import consulo.java.impl.codeInsight.JavaCodeInsightSettings;
import consulo.language.editor.generation.ClassMember;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.actions.generate.GroovyGenerationInfo;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Dmitry.Krasilschikov
 * @since 2008-05-28
 */
public class GroovyGenerateEqualsHandler extends GenerateMembersHandlerBase {
    private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.actions.generate.equals.EqualsGenerateHandler");
    private PsiField[] myEqualsFields = null;
    private PsiField[] myHashCodeFields = null;
    private PsiField[] myNonNullFields = null;
    private static final PsiElementClassMember[] DUMMY_RESULT = new PsiElementClassMember[1];

    public GroovyGenerateEqualsHandler() {
        super(LocalizeValue.empty());
    }

    @Nullable
    @Override
    @RequiredUIAccess
    protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
        myEqualsFields = null;
        myHashCodeFields = null;
        myNonNullFields = PsiField.EMPTY_ARRAY;

        GlobalSearchScope scope = aClass.getResolveScope();
        PsiMethod equalsMethod = GroovyGenerateEqualsHelper
            .findMethod(aClass, GroovyGenerateEqualsHelper.getEqualsSignature(project, scope));
        PsiMethod hashCodeMethod = GroovyGenerateEqualsHelper.findMethod(aClass, GroovyGenerateEqualsHelper.getHashCodeSignature());

        boolean needEquals = equalsMethod == null;
        boolean needHashCode = hashCodeMethod == null;
        if (!needEquals && !needHashCode) {
            LocalizeValue text = aClass instanceof PsiAnonymousClass
                ? GroovyCodeInsightLocalize.generateEqualsAndHashcodeAlreadyDefinedWarningAnonymous()
                : GroovyCodeInsightLocalize.generateEqualsAndHashcodeAlreadyDefinedWarning(aClass.getQualifiedName());

            if (Messages.showYesNoDialog(
                project,
                text.get(),
                GroovyCodeInsightLocalize.generateEqualsAndHashcodeAlreadyDefinedTitle().get(),
                UIUtil.getQuestionIcon()
            ) == DialogWrapper.OK_EXIT_CODE) {
                if (!project.getApplication().runWriteAction((Supplier<Boolean>) () -> {
                    try {
                        equalsMethod.delete();
                        hashCodeMethod.delete();
                        return Boolean.TRUE;
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                        return Boolean.FALSE;
                    }
                })) {
                    return null;
                }
                else {
                    needEquals = needHashCode = true;
                }
            }
            else {
                return null;
            }
        }

        GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
        wizard.show();
        if (!wizard.isOK()) {
            return null;
        }
        myEqualsFields = wizard.getEqualsFields();
        myHashCodeFields = wizard.getHashCodeFields();
        myNonNullFields = wizard.getNonNullFields();
        return DUMMY_RESULT;
    }

    @Nonnull
    @Override
    protected List<? extends GenerationInfo> generateMemberPrototypes(
        PsiClass aClass,
        ClassMember[] originalMembers
    ) throws IncorrectOperationException {
        Project project = aClass.getProject();
        boolean useInstanceOfToCheckParameterType = JavaCodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;

        GroovyGenerateEqualsHelper helper =
            new GroovyGenerateEqualsHelper(
                project,
                aClass,
                myEqualsFields,
                myHashCodeFields,
                myNonNullFields,
                useInstanceOfToCheckParameterType
            );
        Collection<PsiMethod> methods = helper.generateMembers();
        return ContainerUtil.map2List(methods, GroovyGenerationInfo::new);
    }

    @Override
    protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
        return ClassMember.EMPTY_ARRAY;
    }

    @Override
    protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException {
        return GenerationInfo.EMPTY_ARRAY;
    }

    @Override
    protected void cleanup() {
        super.cleanup();

        myEqualsFields = null;
        myHashCodeFields = null;
        myNonNullFields = null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
