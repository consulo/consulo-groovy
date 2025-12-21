/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.JBPopup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.GroovyExtractChooser;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.impl.refactoring.ui.MethodOrClosureScopeChooser;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.impl.refactoring.HelpID.GROOVY_INTRODUCE_PARAMETER;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterHandler implements RefactoringActionHandler, MethodOrClosureScopeChooser.JBPopupOwner {
    private JBPopup myEnclosingMethodsPopup;

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            int offset = editor.getCaretModel().getOffset();

            List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, offset, false);
            if (expressions.isEmpty()) {
                GrVariable variable = GrIntroduceHandlerBase.findVariableAtCaret(file, editor, offset);
                if (variable == null || variable instanceof GrField || variable instanceof GrParameter) {
                    selectionModel.selectLineAtCaret();
                }
                else {
                    TextRange textRange = variable.getTextRange();
                    selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
                }
            }
            else if (expressions.size() == 1) {
                TextRange textRange = expressions.get(0).getTextRange();
                selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
            }
            else {
                IntroduceTargetChooser.showChooser(
                    editor,
                    expressions,
                    selectedValue -> invoke(
                        project,
                        editor,
                        file,
                        selectedValue.getTextRange().getStartOffset(),
                        selectedValue.getTextRange().getEndOffset()
                    ),
                    grExpression -> grExpression.getText()
                );
                return;
            }
        }
        invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    }

    @RequiredUIAccess
    private void invoke(Project project, Editor editor, PsiFile file, int startOffset, int endOffset) {
        try {
            InitialInfo initialInfo = GroovyExtractChooser.invoke(project, editor, file, startOffset, endOffset, false);
            findScope(initialInfo, editor);
        }
        catch (GrRefactoringError e) {
            if (Application.get().isUnitTestMode()) {
                throw e;
            }
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                LocalizeValue.ofNullable(e.getMessage()),
                RefactoringLocalize.introduceParameterTitle(),
                GROOVY_INTRODUCE_PARAMETER
            );
        }
    }

    @RequiredUIAccess
    private void findScope(@Nonnull InitialInfo initialInfo, @Nonnull Editor editor) {
        PsiElement place = initialInfo.getContext();
        List<GrParameterListOwner> scopes = new ArrayList<>();
        while (true) {
            GrParameterListOwner parent = PsiTreeUtil.getParentOfType(place, GrMethod.class, GrClosableBlock.class);
            if (parent == null) {
                break;
            }
            scopes.add(parent);
            place = parent;
        }

        if (scopes.size() == 0) {
            throw new GrRefactoringError(GroovyRefactoringLocalize.thereIsNoMethodOrClosure().get());
        }
        else if (scopes.size() == 1 || Application.get().isUnitTestMode()) {
            GrParameterListOwner owner = scopes.get(0);
            PsiElement toSearchFor;
            if (owner instanceof GrMethod method) {
                toSearchFor = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor());
                if (toSearchFor == null) {
                    return; //if it is null, refactoring was canceled
                }
            }
            else {
                toSearchFor = MethodOrClosureScopeChooser.findVariableToUse(owner);
            }
            showDialog(new IntroduceParameterInfoImpl(initialInfo, owner, toSearchFor));
        }
        else {
            myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(
                scopes,
                editor,
                this,
                (owner, element) -> {
                    showDialog(new IntroduceParameterInfoImpl(initialInfo, owner, element));
                    return null;
                }
            );
            EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, myEnclosingMethodsPopup);
        }
    }

    @Override
    public JBPopup get() {
        return myEnclosingMethodsPopup;
    }

    //method to hack in tests
    @RequiredUIAccess
    protected void showDialog(IntroduceParameterInfo info) {
        new GrIntroduceParameterDialog(info).show();
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        // Does nothing
    }
}
