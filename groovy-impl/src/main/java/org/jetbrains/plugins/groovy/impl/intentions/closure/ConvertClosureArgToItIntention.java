/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.closure;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.HashSet;
import java.util.Set;

public class ConvertClosureArgToItIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.convertClosureArgToItIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new SingleArgClosurePredicate();
    }

    public void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrClosableBlock closure =
            (GrClosableBlock) element;

        GrParameterList parameterList = closure.getParameterList();
        final GrParameter parameter = parameterList.getParameters()[0];
        final Set<GrReferenceExpression> referencesToChange = new HashSet<GrReferenceExpression>();
        GroovyRecursiveElementVisitor visitor = new GroovyRecursiveElementVisitor() {
            public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
                super.visitReferenceExpression(referenceExpression);
                if (!referenceExpression.getText().equals(parameter.getName())) {
                    return;
                }
                PsiElement referent = referenceExpression.resolve();
                if (parameter.equals(referent)) {
                    referencesToChange.add(referenceExpression);
                }
            }
        };
        closure.accept(visitor);
        parameter.delete();
        for (GrReferenceExpression referenceExpression : referencesToChange) {
            PsiImplUtil.replaceExpression("it", referenceExpression);
        }
    }

}
