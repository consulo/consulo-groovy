package org.jetbrains.plugins.groovy.impl.template;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.context.EverywhereContextType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GroovyGenericTemplateContextType extends GroovyTemplateContextType {
    public GroovyGenericTemplateContextType() {
        super("GROOVY", LocalizeValue.localizeTODO("Groovy"), EverywhereContextType.class);
    }

    @RequiredReadAction
    @Override
    protected boolean isInContext(@Nonnull PsiElement element) {
        return true;
    }
}
