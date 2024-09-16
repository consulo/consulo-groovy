package org.jetbrains.plugins.groovy.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaLikeStatementContextType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

@ExtensionImpl
public class GroovyStatementTemplateContextType extends GroovyTemplateContextType implements JavaLikeStatementContextType {
    public GroovyStatementTemplateContextType() {
        super("GROOVY_STATEMENT", LocalizeValue.localizeTODO("Statement"), GroovyGenericTemplateContextType.class);
    }

    @RequiredReadAction
    @Override
    protected boolean isInContext(@Nonnull PsiElement element) {
        PsiElement stmt = PsiTreeUtil.findFirstParent(element, PsiUtil::isExpressionStatement);

        return !isAfterExpression(element) && stmt != null && stmt.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
    }
}
