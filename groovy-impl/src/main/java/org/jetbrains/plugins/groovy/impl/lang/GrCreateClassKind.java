package org.jetbrains.plugins.groovy.impl.lang;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.ClassKind;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 * @since 2014-05-28
 */
public enum GrCreateClassKind implements ClassKind {
    CLASS(JavaLanguageLocalize.elementClassNominative()),
    INTERFACE(JavaLanguageLocalize.elementInterfaceNominative()),
    TRAIT(LocalizeValue.localizeTODO("trait")),
    ENUM(JavaLanguageLocalize.elementEnumNominative()),
    ANNOTATION(JavaLanguageLocalize.elementAnnotationNominative());

    private final LocalizeValue myDescription;

    GrCreateClassKind(@Nonnull LocalizeValue description) {
        myDescription = description;
    }

    @Override
    public LocalizeValue getDescriptionValue() {
        return myDescription;
    }

    @Override
    public String getDescription() {
        return myDescription.get();
    }
}
