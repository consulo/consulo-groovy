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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.dsl.psi.PsiClassCategory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class NewInstanceOfSingletonInspection extends BaseInspection {
    private static final Logger LOG = Logger.getInstance(NewInstanceOfSingletonInspection.class);

    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            public void visitNewExpression(GrNewExpression newExpression) {
                super.visitNewExpression(newExpression);

                GrCodeReferenceElement refElement = newExpression.getReferenceElement();
                if (refElement != null
                    && newExpression.getArrayDeclaration() == null
                    && refElement.resolve() instanceof GrTypeDefinition typeDef
                    && PsiClassCategory.hasAnnotation(typeDef, GroovyCommonClassNames.GROOVY_LANG_SINGLETON)) {
                    registerError(newExpression, GroovyInspectionLocalize.newInstanceOfSingleton().get());
                }
            }
        };
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected GroovyFix buildFix(@Nonnull final PsiElement location) {
        GrCodeReferenceElement refElement = ((GrNewExpression) location).getReferenceElement();
        LOG.assertTrue(refElement != null);
        final GrTypeDefinition singleton = (GrTypeDefinition) refElement.resolve();
        LOG.assertTrue(singleton != null);

        return new GroovyFix() {
            @Override
            protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
                GrExpression instanceRef =
                    GroovyPsiElementFactory.getInstance(project).createExpressionFromText(singleton.getQualifiedName() + ".instance");

                GrExpression replaced = ((GrNewExpression) location).replaceWithExpression(instanceRef, true);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
            }

            @Nonnull
            @Override
            public LocalizeValue getName() {
                return GroovyInspectionLocalize.replaceNewExpressionWith0Instance(singleton.getName());
            }
        };
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Override
    protected String buildErrorString(Object... args) {
        return (String) args[0];
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("New instance of class annotated with @groovy.lang.Singleton");
    }
}
