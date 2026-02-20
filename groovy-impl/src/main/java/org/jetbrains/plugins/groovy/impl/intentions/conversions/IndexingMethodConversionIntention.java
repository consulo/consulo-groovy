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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class IndexingMethodConversionIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.indexingMethodConversionIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new IndexingMethodConversionPredicate();
    }

    public void processIntention(
        @Nonnull PsiElement element,
        Project project,
        Editor editor
    ) throws IncorrectOperationException {
        GrMethodCallExpression callExpression = (GrMethodCallExpression) element;
        GrArgumentList argList = callExpression.getArgumentList();
        GrExpression[] arguments = argList.getExpressionArguments();

        GrReferenceExpression methodExpression = (GrReferenceExpression) callExpression.getInvokedExpression();
        IElementType referenceType = methodExpression.getDotTokenType();

        String methodName = methodExpression.getReferenceName();
        GrExpression qualifier = methodExpression.getQualifierExpression();
        if ("getAt".equals(methodName) || "get".equals(methodName)) {
            PsiImplUtil.replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + ']', callExpression);
        }
        else {
            PsiImplUtil.replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + "]=" + arguments[1]
                .getText(), callExpression);
        }
    }

}
