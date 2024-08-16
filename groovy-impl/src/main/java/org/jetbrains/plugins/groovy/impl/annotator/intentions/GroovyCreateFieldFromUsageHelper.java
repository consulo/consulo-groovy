/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.GuessTypeParameters;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.Language;
import consulo.language.editor.template.EmptyExpression;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyCreateFieldFromUsageHelper implements CreateFieldFromUsageHelper {
    @Override
    @RequiredWriteAction
    public Template setupTemplateImpl(
        PsiField f,
        Object expectedTypes,
        PsiClass targetClass,
        Editor editor,
        PsiElement context,
        boolean createConstantField,
        PsiSubstitutor substitutor
    ) {
        GrVariableDeclaration fieldDecl = (GrVariableDeclaration)f.getParent();
        GrField field = (GrField)fieldDecl.getVariables()[0];

        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(fieldDecl);

        Project project = context.getProject();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

        if (expectedTypes instanceof TypeConstraint[] expectedTypeConstraints) {
            GrTypeElement typeElement = fieldDecl.getTypeElementGroovy();
            assert typeElement != null;
            ChooseTypeExpression expr =
                new ChooseTypeExpression(expectedTypeConstraints, PsiManager.getInstance(project), typeElement.getResolveScope());
            builder.replaceElement(typeElement, expr);
        }
        else if (expectedTypes instanceof ExpectedTypeInfo[] expectedTypeInfos) {
            new GuessTypeParameters(factory).setupTypeElement(
                field.getTypeElement(),
                expectedTypeInfos,
                substitutor,
                builder,
                context,
                targetClass
            );
        }
        if (createConstantField) {
            field.setInitializerGroovy(factory.createExpressionFromText("0", null));
            builder.replaceElement(field.getInitializerGroovy(), new EmptyExpression());
        }

        fieldDecl = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(fieldDecl);
        Template template = builder.buildTemplate();

        TextRange range = fieldDecl.getTextRange();
        editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

        if (expectedTypes instanceof ExpectedTypeInfo[] expectedTypeInfos && expectedTypeInfos.length > 1) {
            template.setToShortenLongNames(false);
        }
        return template;
    }

    @Override
    public PsiField insertFieldImpl(@Nonnull PsiClass targetClass, @Nonnull PsiField field, @Nonnull PsiElement place) {
        if (targetClass instanceof GroovyScriptClass) {
            PsiElement added = targetClass.getContainingFile().add(field.getParent());
            return (PsiField)((GrVariableDeclaration)added).getVariables()[0];
        }
        else {
            return (PsiField)targetClass.add(field);
        }
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return GroovyLanguage.INSTANCE;
    }
}
