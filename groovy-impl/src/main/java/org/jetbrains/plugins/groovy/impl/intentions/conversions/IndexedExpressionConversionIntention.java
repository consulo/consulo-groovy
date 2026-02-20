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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class IndexedExpressionConversionIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.indexedExpressionConversionIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new IndexedExpressionConversionPredicate();
    }

    public void processIntention(
        @Nonnull PsiElement element,
        Project project,
        Editor editor
    ) throws IncorrectOperationException {

        GrIndexProperty arrayIndexExpression = (GrIndexProperty) element;

        GrArgumentList argList = (GrArgumentList) arrayIndexExpression.getLastChild();

        assert argList != null;
        GrExpression[] arguments = argList.getExpressionArguments();

        PsiElement parent = element.getParent();
        GrExpression arrayExpression = arrayIndexExpression.getInvokedExpression();
        if (!(parent instanceof GrAssignmentExpression)) {
            rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
            return;
        }
        GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) parent;
        GrExpression rhs = assignmentExpression.getRValue();
        if (rhs.equals(element)) {
            rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
        }
        else {
            rewriteAsSetAt(assignmentExpression, arrayExpression, arguments[0], rhs);
        }
    }

    private static void rewriteAsGetAt(
        GrIndexProperty arrayIndexExpression,
        GrExpression arrayExpression,
        GrExpression argument
    ) throws IncorrectOperationException {
        PsiImplUtil.replaceExpression(
            arrayExpression.getText() + ".getAt(" + argument.getText() + ')',
            arrayIndexExpression
        );
    }

    private static void rewriteAsSetAt(
        GrAssignmentExpression assignment,
        GrExpression arrayExpression,
        GrExpression argument,
        GrExpression value
    ) throws IncorrectOperationException {
        PsiImplUtil.replaceExpression(arrayExpression.getText() + ".putAt(" + argument.getText() + ", " +
            "" + value.getText() + ')', assignment);
    }

}
