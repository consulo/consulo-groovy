/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Dmitry.Krasilschikov
 * @since 2007-09-21
 */
public class ChangeExtendsImplementsQuickFix implements SyntheticIntentionAction {
    @Nullable
    private final GrExtendsClause myExtendsClause;
    @Nullable
    private final GrImplementsClause myImplementsClause;
    @Nonnull
    private final GrTypeDefinition myClass;

    public ChangeExtendsImplementsQuickFix(@Nonnull GrTypeDefinition aClass) {
        myClass = aClass;
        myExtendsClause = aClass.getExtendsClause();
        myImplementsClause = aClass.getImplementsClause();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyLocalize.changeImplementsAndExtendsClasses();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return myClass.isValid() && myClass.getManager().isInProject(file);
    }

    @Override
    @RequiredWriteAction
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        Set<String> classes = new LinkedHashSet<>();
        Set<String> interfaces = new LinkedHashSet<>();
        Set<String> unknownClasses = new LinkedHashSet<>();
        Set<String> unknownInterfaces = new LinkedHashSet<>();

        if (myExtendsClause != null) {
            Set<String> unknown = myClass.isInterface() ? unknownInterfaces : unknownClasses;
            collectRefs(myExtendsClause.getReferenceElementsGroovy(), classes, interfaces, unknown);
            myExtendsClause.delete();
        }

        if (myImplementsClause != null) {
            collectRefs(myImplementsClause.getReferenceElementsGroovy(), classes, interfaces, unknownInterfaces);
            myImplementsClause.delete();
        }

        if (myClass.isInterface()) {
            interfaces.addAll(classes);
            unknownInterfaces.addAll(unknownClasses);
            addNewClause(interfaces, unknownInterfaces, project, true);
        }
        else {
            addNewClause(classes, unknownClasses, project, true);
            addNewClause(interfaces, unknownInterfaces, project, false);
        }
    }

    @RequiredReadAction
    private static void collectRefs(
        GrCodeReferenceElement[] refs,
        Collection<String> classes,
        Collection<String> interfaces,
        Collection<String> unknown
    ) {
        for (GrCodeReferenceElement ref : refs) {
            PsiElement extendsElement = ref.resolve();
            String canonicalText = ref.getCanonicalText();

            if (extendsElement instanceof PsiClass psiClass) {
                if (psiClass.isInterface()) {
                    interfaces.add(canonicalText);
                }
                else {
                    classes.add(canonicalText);
                }
            }
            else {
                unknown.add(canonicalText);
            }
        }
    }

    @RequiredWriteAction
    private void addNewClause(
        Collection<String> elements,
        Collection<String> additional,
        Project project,
        boolean isExtends
    ) throws IncorrectOperationException {
        if (elements.isEmpty() && additional.isEmpty()) {
            return;
        }

        StringBuilder classText = new StringBuilder();
        classText.append("class A ").append(isExtends ? "extends " : "implements ");

        for (String str : elements) {
            classText.append(str).append(", ");
        }

        for (String str : additional) {
            classText.append(str).append(", ");
        }

        classText.delete(classText.length() - 2, classText.length());

        classText.append(" {}");

        GrTypeDefinition definition = GroovyPsiElementFactory.getInstance(project).createTypeDefinition(classText.toString());
        GroovyPsiElement clause = isExtends ? definition.getExtendsClause() : definition.getImplementsClause();
        assert clause != null;

        PsiElement addedClause = myClass.addBefore(clause, myClass.getBody());
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedClause);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
