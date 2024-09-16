package org.jetbrains.plugins.groovy.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaLikeExpressionContextType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

@ExtensionImpl
public class GroovyExpressionTemplateContextType extends GroovyTemplateContextType implements JavaLikeExpressionContextType {

    public GroovyExpressionTemplateContextType() {
        super("GROOVY_EXPRESSION", LocalizeValue.localizeTODO("Expression"), GroovyGenericTemplateContextType.class);
    }

    @RequiredReadAction
    @Override
    protected boolean isInContext(@Nonnull PsiElement element) {
        return isExpressionContext(element);
    }

    @RequiredReadAction
    private static boolean isExpressionContext(PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrReferenceExpression)) {
            return false;
        }
        if (((GrReferenceExpression) parent).isQualified()) {
            return false;
        }
        if (parent.getParent() instanceof GrCall) {
            return false;
        }
        return !isAfterExpression(element);
    }
}
