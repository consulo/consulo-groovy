package org.jetbrains.plugins.groovy.impl.intentions.declaration;

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Groovy Introduce local variable intention.
 *
 * @author siosio
 */
public class GrIntroduceLocalVariableIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.grIntroduceLocalVariableIntentionName();
    }

    @RequiredReadAction
    protected PsiElement getTargetExpression(@Nonnull PsiElement element) {
        if (isTargetVisible(element)) {
            return element;
        }
        PsiElement expression = PsiTreeUtil.getParentOfType(element, GrExpression.class);
        return expression == null ? null : getTargetExpression(expression);
    }

    @RequiredReadAction
    private static boolean isTargetVisible(PsiElement element) {
        return PsiUtil.isExpressionStatement(element)
            && element instanceof GrExpression expression
            && expression.getType() != PsiType.VOID
            && PsiTreeUtil.getParentOfType(element, GrAssignmentExpression.class) == null;
    }

    @RequiredReadAction
    protected void setSelection(Editor editor, PsiElement element) {
        int offset = element.getTextOffset();
        int length = element.getTextLength();
        editor.getSelectionModel().setSelection(offset, offset + length);
    }

    @Override
    @RequiredReadAction
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        setSelection(editor, getTargetExpression(element));
        new GrIntroduceVariableHandler().invoke(project, editor, element.getContainingFile(), null);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected PsiElementPredicate getElementPredicate() {
        return element -> element != null && getTargetExpression(element) != null;
    }
}

