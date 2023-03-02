package org.jetbrains.plugins.groovy.impl.intentions.declaration;

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiType;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable.GrIntroduceVariableHandler;

/**
 * Groovy Introduce local variable intention.
 *
 * @author siosio
 */
public class GrIntroduceLocalVariableIntention extends Intention {

  protected PsiElement getTargetExpression(@Nonnull PsiElement element) {
    if (isTargetVisible(element)) {
      return element;
    }
    PsiElement expression = PsiTreeUtil.getParentOfType(element, GrExpression.class);
    return expression == null ? null : getTargetExpression(expression);
  }

  private static boolean isTargetVisible(PsiElement element) {
    if (PsiUtil.isExpressionStatement(element) && element instanceof GrExpression) {
      if (((GrExpression)element).getType() != PsiType.VOID) {
        if (PsiTreeUtil.getParentOfType(element, GrAssignmentExpression.class) == null) {
          return true;
        }
      }
    }
    return false;
  }

  protected void setSelection(Editor editor, PsiElement element) {
    int offset = element.getTextOffset();
    int length = element.getTextLength();
    editor.getSelectionModel().setSelection(offset, offset + length);
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    setSelection(editor, getTargetExpression(element));
    new GrIntroduceVariableHandler().invoke(project, editor, element.getContainingFile(), null);
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (element == null) {
          return false;
        }
        return getTargetExpression(element) != null;
      }
    };
  }
}

