package org.jetbrains.plugins.groovy.impl.template;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

@ExtensionImpl
public class GroovyDeclarationTemplateContextType extends GroovyTemplateContextType {
    public GroovyDeclarationTemplateContextType() {
        super("GROOVY_DECLARATION", LocalizeValue.localizeTODO("Declaration"), GroovyGenericTemplateContextType.class);
    }

    @RequiredReadAction
    @Override
    protected boolean isInContext(@Nonnull PsiElement element) {
        if (PsiTreeUtil.getParentOfType(element, GrCodeBlock.class, false, GrTypeDefinition.class) != null) {
            return false;
        }

        if (element instanceof PsiComment) {
            return false;
        }

        return GroovyCompletionData.suggestClassInterfaceEnum(element) || GroovyCompletionData.suggestFinalDef(element);
    }
}
