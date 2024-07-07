package org.jetbrains.plugins.groovy.impl.codeInspection.style;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

public class JavaStylePropertiesInvocationFixer implements LocalQuickFix {
  @Nonnull
  @Override
  public String getName() {
    return GroovyIntentionsBundle.message("java.style.properties.invocation.intention.name");
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("java.style.properties.invocation.intention.family.name");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement pparent = descriptor.getPsiElement().getParent().getParent();
    if (pparent instanceof GrMethodCall){
      JavaStylePropertiesUtil.fixJavaStyleProperty((GrMethodCall)pparent);
    }
  }
}
