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

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopup;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrChangeSignatureDialog;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrMethodDescriptor;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrParameterInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.impl.refactoring.ui.MethodOrClosureScopeChooser;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class CreateParameterFromUsageFix extends Intention implements SyntheticIntentionAction, MethodOrClosureScopeChooser.JBPopupOwner {
    private final String myName;
    private JBPopup myEnclosingMethodsPopup = null;

    public CreateParameterFromUsageFix(GrReferenceExpression ref) {
        myName = ref.getReferenceName();
    }

    @Nonnull
    @Override
    public String getText() {
        return GroovyLocalize.createParameterFromUsage(myName).get();
    }

    @Nonnull
    @Override
    public String getFamilyName() {
        return GroovyLocalize.createFromUsageFamilyName().get();
    }

    @Override
    public JBPopup get() {
        return myEnclosingMethodsPopup;
    }

    @Override
    @RequiredReadAction
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        if (element instanceof GrReferenceExpression referenceExpression) {
            findScope(referenceExpression, editor, project);
        }
    }

    @Override
    protected boolean isStopElement(PsiElement element) {
        return element instanceof GrExpression;
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return element -> element instanceof GrReferenceExpression;
    }

    @RequiredReadAction
    private void findScope(@Nonnull final GrReferenceExpression ref, @Nonnull final Editor editor, final Project project) {
        PsiElement place = ref;
        final List<GrMethod> scopes = new ArrayList<>();
        while (true) {
            final GrMethod parent = PsiTreeUtil.getParentOfType(place, GrMethod.class);
            if (parent == null) {
                break;
            }
            scopes.add(parent);
            place = parent;
        }

        if (scopes.size() == 1) {
            final GrMethod owner = scopes.get(0);
            final PsiMethod toSearchFor;
            toSearchFor = SuperMethodWarningUtil.checkSuperMethod(owner, RefactoringLocalize.toRefactor().get());
            if (toSearchFor == null) {
                return; //if it is null, refactoring was canceled
            }
            showDialog(toSearchFor, ref, project);
        }
        else if (scopes.size() > 1) {
            myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(
                scopes,
                editor,
                this,
                (owner, element) -> {
                    showDialog((PsiMethod)owner, ref, project);
                    return null;
                }
            );
            EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, myEnclosingMethodsPopup);
        }
    }

    private static void showDialog(final PsiMethod method, final GrReferenceExpression ref, final Project project) {
        project.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            final String name = ref.getReferenceName();
            final List<PsiType> types = GroovyExpectedTypesProvider.getDefaultExpectedTypes(ref);

            PsiType unboxed = types.isEmpty() ? null : TypesUtil.unboxPrimitiveTypeWrapper(types.get(0));
            @Nonnull final PsiType type = unboxed != null ? unboxed : PsiType.getJavaLangObject(
                ref.getManager(),
                ref.getResolveScope()
            );

            if (method instanceof GrMethod) {
                GrMethodDescriptor descriptor = new GrMethodDescriptor((GrMethod)method);
                GrChangeSignatureDialog dialog = new GrChangeSignatureDialog(project, descriptor, true, ref);

                List<GrParameterInfo> parameters = dialog.getParameters();
                parameters.add(createParameterInfo(name, type));
                dialog.setParameterInfos(parameters);
                dialog.show();
            }
            else if (method != null) {
                JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(project, method, false, ref);
                final List<ParameterInfoImpl> parameterInfos = new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
                ParameterInfoImpl parameterInfo =
                    new ParameterInfoImpl(-1, name, type, PsiTypesUtil.getDefaultValueOfType(type), false);
                if (!method.isVarArgs()) {
                    parameterInfos.add(parameterInfo);
                }
                else {
                    parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
                }
                dialog.setParameterInfos(parameterInfos);
                dialog.show();
            }
        });
    }

    private static GrParameterInfo createParameterInfo(String name, PsiType type) {
        String notNullName = name != null ? name : "";
        String defaultValueText = GroovyToJavaGenerator.getDefaultValueText(type.getCanonicalText());
        return new GrParameterInfo(notNullName, defaultValueText, "", type, -1, false);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
