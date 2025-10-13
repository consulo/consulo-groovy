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
package org.jetbrains.plugins.groovy.impl.intentions.base;

import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.impl.intentions.utils.BoolUtils;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;


public abstract class Intention implements IntentionAction {
    private final PsiElementPredicate predicate;

    /**
     * @noinspection AbstractMethodCallInConstructor, OverridableMethodCallInConstructor
     */
    protected Intention() {
        super();
        predicate = getElementPredicate();
    }

    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!QuickfixUtil.ensureFileWritable(project, file)) {
            return;
        }
        final PsiElement element = findMatchingElement(file, editor);
        if (element == null) {
            return;
        }
        assert element.isValid() : element;
        processIntention(element, project, editor);
    }

    protected abstract void processIntention(
        @Nonnull PsiElement element,
        Project project,
        Editor editor
    ) throws IncorrectOperationException;

    @Nonnull
    protected abstract PsiElementPredicate getElementPredicate();


    protected static void replaceExpressionWithNegatedExpressionString(
        @Nonnull String newExpression,
        @Nonnull GrExpression expression
    ) throws IncorrectOperationException {
        final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());

        GrExpression expressionToReplace = expression;
        final String expString;
        if (BoolUtils.isNegated(expression)) {
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpression;
        }
        else {
            expString = "!(" + newExpression + ')';
        }
        final GrExpression newCall =
            factory.createExpressionFromText(expString);
        assert expressionToReplace != null;
        expressionToReplace.replaceWithExpression(newCall, true);
    }


    @Nullable
    PsiElement findMatchingElement(PsiFile file, Editor editor) {
        if (!(file instanceof GroovyFileBase)) {
            return null;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
            TextRange selectionRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
            PsiElement element = GroovyRefactoringUtil
                .findElementInRange(file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), PsiElement.class);
            while (element != null && element.getTextRange() != null && selectionRange.contains(element.getTextRange())) {
                if (predicate.satisfiedBy(element)) {
                    return element;
                }
                element = element.getParent();
            }
        }

        final int position = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(position);
        while (element != null) {
            if (predicate.satisfiedBy(element)) {
                return element;
            }
            if (isStopElement(element)) {
                break;
            }
            element = element.getParent();
        }

        element = file.findElementAt(position - 1);
        while (element != null) {
            if (predicate.satisfiedBy(element)) {
                return element;
            }
            if (isStopElement(element)) {
                return null;
            }
            element = element.getParent();
        }

        return null;
    }

    protected boolean isStopElement(PsiElement element) {
        return element instanceof PsiFile;
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return findMatchingElement(file, editor) != null;
    }

    public boolean startInWriteAction() {
        return true;
    }
}
