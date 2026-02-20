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
package org.jetbrains.plugins.groovy.impl.intentions.control;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class ExpandBooleanIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.expandBooleanIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ExpandBooleanPredicate();
    }

    public void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrStatement containingStatement = (GrStatement) element;
        if (ExpandBooleanPredicate.isBooleanAssignment(containingStatement)) {
            GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) containingStatement;
            GrExpression rhs = assignmentExpression.getRValue();
            assert rhs != null;
            String rhsText = rhs.getText();
            GrExpression lhs = assignmentExpression.getLValue();
            String lhsText = lhs.getText();
            String statement = "if(" + rhsText + "){\n" + lhsText + " = true\n}else{\n" + lhsText + " = false\n}";
            PsiImplUtil.replaceStatement(statement, containingStatement);
        }
        else if (ExpandBooleanPredicate.isBooleanReturn(containingStatement)) {
            GrReturnStatement returnStatement = (GrReturnStatement) containingStatement;
            GrExpression returnValue = returnStatement.getReturnValue();
            String valueText = returnValue.getText();
            String statement = "if(" + valueText + "){\nreturn true\n}else{\nreturn false\n}";
            PsiImplUtil.replaceStatement(statement, containingStatement);
        }
    }
}
