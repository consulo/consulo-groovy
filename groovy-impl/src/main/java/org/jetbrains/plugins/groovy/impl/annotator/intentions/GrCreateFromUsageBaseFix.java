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

import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.groovy.localize.GroovyLocalize;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.popup.JBPopup;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public abstract class GrCreateFromUsageBaseFix extends Intention implements SyntheticIntentionAction {
    protected final SmartPsiElementPointer<GrReferenceExpression> myRefExpression;

    public GrCreateFromUsageBaseFix(@Nonnull GrReferenceExpression refExpression) {
        myRefExpression = SmartPointerManager.getInstance(refExpression.getProject()).createSmartPsiElementPointer(refExpression);
    }

    @Override
    @Nonnull
    public String getFamilyName() {
        return GroovyLocalize.createFromUsageFamilyName().get();
    }

    @RequiredReadAction
    protected GrReferenceExpression getRefExpr() {
        return myRefExpression.getElement();
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        final GrReferenceExpression element = myRefExpression.getElement();
        if (element == null || !element.isValid()) {
            return false;
        }

        List<PsiClass> targetClasses = getTargetClasses();
        return !targetClasses.isEmpty();
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }


    @Override
    @RequiredUIAccess
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        final List<PsiClass> classes = getTargetClasses();
        if (classes.size() == 1) {
            invokeImpl(project, classes.get(0));
        }
        else if (!classes.isEmpty()) {
            chooseClass(classes, editor);
        }
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return element -> element instanceof GrReferenceExpression;
    }

    private void chooseClass(List<PsiClass> classes, Editor editor) {
        final Project project = classes.get(0).getProject();

        final JList<PsiClass> list = new JBList<>(classes);
        PsiElementListCellRenderer renderer = new PsiClassListCellRenderer();
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(renderer);
        final PopupChooserBuilder builder = new PopupChooserBuilder(list);
        renderer.installSpeedSearch(builder);

        Runnable runnable = () -> {
            int index = list.getSelectedIndex();
            if (index < 0) {
                return;
            }
            final PsiClass aClass = list.getSelectedValue();
            CommandProcessor.getInstance().executeCommand(
                project,
                () -> aClass.getApplication().runWriteAction(() -> invokeImpl(project, aClass)),
                getText(),
                null
            );
        };

        JBPopup popup = builder.setTitle(JavaQuickFixBundle.message("target.class.chooser.title"))
            .setItemChoosenCallback(runnable)
            .createPopup();

        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
    }

    @RequiredUIAccess
    protected abstract void invokeImpl(Project project, @Nonnull PsiClass targetClass);

    @RequiredReadAction
    private List<PsiClass> getTargetClasses() {
        final GrReferenceExpression ref = getRefExpr();
        final boolean compileStatic = PsiUtil.isCompileStatic(ref) || GrStaticChecker.isPropertyAccessInStaticMethod(ref);
        final PsiClass targetClass = QuickfixUtil.findTargetClass(ref, compileStatic);
        if (targetClass == null || !canBeTargetClass(targetClass)) {
            return Collections.emptyList();
        }

        final ArrayList<PsiClass> classes = new ArrayList<>();
        collectSupers(targetClass, classes);
        return classes;
    }

    private void collectSupers(PsiClass psiClass, ArrayList<PsiClass> classes) {
        classes.add(psiClass);

        final PsiClass[] supers = psiClass.getSupers();
        for (PsiClass aSuper : supers) {
            if (classes.contains(aSuper)) {
                continue;
            }
            if (canBeTargetClass(aSuper)) {
                collectSupers(aSuper, classes);
            }
        }
    }

    protected boolean canBeTargetClass(PsiClass psiClass) {
        return psiClass.getManager().isInProject(psiClass);
    }
}
