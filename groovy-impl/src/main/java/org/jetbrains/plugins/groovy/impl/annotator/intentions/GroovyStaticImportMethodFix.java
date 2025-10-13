/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.proximity.PsiProximityComparator;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyStaticImportMethodFix implements SyntheticIntentionAction {
    private final SmartPsiElementPointer<GrMethodCall> myMethodCall;
    private List<PsiMethod> myCandidates = null;

    public GroovyStaticImportMethodFix(@Nonnull GrMethodCall methodCallExpression) {
        myMethodCall =
            SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LocalizeValue getText() {
        String text = "Static Import Method";
        if (getCandidates().size() == 1) {
            final int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME;
            text += " '" + PsiFormatUtil.formatMethod(getCandidates().get(0), PsiSubstitutor.EMPTY, options, 0) + "'";
        }
        else {
            text += "...";
        }
        return LocalizeValue.localizeTODO(text);
    }

    @Nullable
    private static GrReferenceExpression getMethodExpression(GrMethodCall call) {
        GrExpression result = call.getInvokedExpression();
        return result instanceof GrReferenceExpression ? (GrReferenceExpression)result : null;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        myCandidates = null;
        return myMethodCall != null
            && myMethodCall.getElement() != null
            && myMethodCall.getElement().isValid()
            && getMethodExpression(myMethodCall.getElement()) != null
            && getMethodExpression(myMethodCall.getElement()).getQualifierExpression() == null
            && file.getManager().isInProject(file)
            && !getCandidates().isEmpty();
    }

    @Nonnull
    @RequiredReadAction
    private List<PsiMethod> getMethodsToImport() {
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myMethodCall.getProject());

        GrMethodCall element = myMethodCall.getElement();
        assert element != null;
        GrReferenceExpression reference = getMethodExpression(element);
        assert reference != null;
        GrArgumentList argumentList = element.getArgumentList();
        String name = reference.getReferenceName();

        ArrayList<PsiMethod> list = new ArrayList<>();
        if (name == null) {
            return list;
        }
        GlobalSearchScope scope = element.getResolveScope();
        PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, 20);
        List<PsiMethod> applicableList = new ArrayList<>();
        for (PsiMethod method : methods) {
            ProgressManager.checkCanceled();
            if (JavaCompletionUtil.isInExcludedPackage(method, false)) {
                continue;
            }
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            PsiFile file = method.getContainingFile();
            if (file instanceof PsiClassOwner classOwner
                //do not show methods from default package
                && classOwner.getPackageName().length() != 0 && PsiUtil.isAccessible(element, method)) {
                list.add(method);
                if (PsiUtil.isApplicable(PsiUtil.getArgumentTypes(element, true), method, PsiSubstitutor.EMPTY, element, false)) {
                    applicableList.add(method);
                }
            }
        }
        List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
        Collections.sort(result, new PsiProximityComparator(argumentList));
        return result;
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        if (getCandidates().size() == 1) {
            final PsiMethod toImport = getCandidates().get(0);
            doImport(toImport);
        }
        else {
            chooseAndImport(editor);
        }
    }

    @RequiredUIAccess
    @SuppressWarnings("RequiredXAction")
    private void doImport(final PsiMethod toImport) {
        CommandProcessor.getInstance().executeCommand(
            toImport.getProject(),
            () -> toImport.getApplication().runWriteAction(() -> {
                GrMethodCall element = myMethodCall.getElement();
                if (element != null) {
                    getMethodExpression(element).bindToElementViaStaticImport(toImport);
                }
            }),
            getText().get(),
            this
        );
    }

    @RequiredUIAccess
    @SuppressWarnings("RequiredXAction")
    private void chooseAndImport(Editor editor) {
        final JList<PsiMethod> list = new JBList<>(getCandidates());
        list.setCellRenderer(new MethodCellRenderer(true));
        new PopupChooserBuilder(list)
            .setTitle(JavaQuickFixLocalize.staticImportMethodChooseMethodToImport().get())
            .setMovable(true)
            .setItemChoosenCallback(() -> {
                PsiMethod selectedValue = list.getSelectedValue();
                if (selectedValue == null) {
                    return;
                }
                assert selectedValue.isValid();
                doImport(selectedValue);
            })
            .createPopup()
            .showInBestPositionFor((DataContext)editor);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @RequiredReadAction
    private List<PsiMethod> getCandidates() {
        if (myCandidates == null) {
            myCandidates = getMethodsToImport();
        }
        return myCandidates;
    }
}
