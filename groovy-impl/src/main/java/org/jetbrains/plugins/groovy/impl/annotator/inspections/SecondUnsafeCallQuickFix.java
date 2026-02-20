/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.annotator.inspections;

import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author Dmitry.Krasilschikov
 * @since 2007-11-13
 */
public class SecondUnsafeCallQuickFix implements LocalQuickFix {
    @Nonnull
    public LocalizeValue getName() {
        return GroovyInspectionLocalize.secondUnsafeCall();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof GrReferenceExpression)) {
            return;
        }

        PsiElement newDot = GroovyPsiElementFactory.getInstance(project).createDotToken(GroovyTokenTypes.mOPTIONAL_DOT.toString());
        ((GrReferenceExpression) element).replaceDotToken(newDot);
    }
}
