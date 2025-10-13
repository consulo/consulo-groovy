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
package org.jetbrains.plugins.groovy.impl.intentions.other;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.CopyPasteManager;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * @author Max Medvedev
 */
public class GrCopyStringConcatenationContentIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.grCopyStringConcatenationContentIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        final StringBuilder buffer = new StringBuilder();
        getValue(element, buffer);

        final Transferable contents = new StringSelection(buffer.toString());
        CopyPasteManager.getInstance().setContents(contents);
    }

    private static void getValue(PsiElement element, StringBuilder buffer) {
        if (element instanceof GrLiteral) {
            buffer.append(((GrLiteral) element).getValue());
        }
        else if (element instanceof GrBinaryExpression) {
            getValue(((GrBinaryExpression) element).getLeftOperand(), buffer);
            getValue(((GrBinaryExpression) element).getRightOperand(), buffer);
        }
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {
            @Override
            public boolean satisfiedBy(PsiElement element) {
                if (element instanceof GrLiteral && ((GrLiteral) element).getValue() instanceof String) {
                    return true;
                }

                return element instanceof GrBinaryExpression &&
                    ((GrBinaryExpression) element).getOperationTokenType() == GroovyTokenTypes.mPLUS &&
                    satisfiedBy(((GrBinaryExpression) element).getLeftOperand()) &&
                    satisfiedBy(((GrBinaryExpression) element).getRightOperand());
            }
        };
    }
}
