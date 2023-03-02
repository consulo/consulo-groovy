package org.jetbrains.plugins.groovy.impl.unwrap;

import consulo.language.editor.CodeInsightBundle;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class GroovyConditionalUnwrapper extends GroovyUnwrapper {
  public GroovyConditionalUnwrapper() {
    super(CodeInsightBundle.message("unwrap.conditional"));
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return e.getParent() instanceof GrConditionalExpression;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    GrConditionalExpression cond = (GrConditionalExpression)element.getParent();

    PsiElement savedBlock;

    if (cond.getElseBranch() == element) {
      savedBlock = element;
    }
    else {
      savedBlock = cond.getThenBranch();
    }

    context.extractElement(savedBlock, cond);

    context.deleteExactly(cond);
  }
}
