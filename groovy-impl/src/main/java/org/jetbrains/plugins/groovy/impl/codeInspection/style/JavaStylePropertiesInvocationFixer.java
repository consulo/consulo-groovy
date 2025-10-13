package org.jetbrains.plugins.groovy.impl.codeInspection.style;

import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

public class JavaStylePropertiesInvocationFixer implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return GroovyIntentionLocalize.javaStylePropertiesInvocationIntentionName();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        final PsiElement pparent = descriptor.getPsiElement().getParent().getParent();
        if (pparent instanceof GrMethodCall) {
            JavaStylePropertiesUtil.fixJavaStyleProperty((GrMethodCall) pparent);
        }
    }
}
