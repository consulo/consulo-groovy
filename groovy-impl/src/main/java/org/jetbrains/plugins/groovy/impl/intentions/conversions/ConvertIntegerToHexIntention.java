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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.math.BigInteger;

public class ConvertIntegerToHexIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.convertIntegerToHexIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ConvertIntegerToHexPredicate();
    }

    public void processIntention(
        @Nonnull PsiElement element,
        Project project,
        Editor editor
    ) throws IncorrectOperationException {
        GrLiteral exp = (GrLiteral) element;
        String textString = exp.getText().replaceAll("_", "");
        int textLength = textString.length();
        char lastChar = textString.charAt(textLength - 1);
        boolean isLong = lastChar == 'l' || lastChar == 'L';
        if (isLong) {
            textString = textString.substring(0, textLength - 1);
        }

        BigInteger val;
        if (textString.startsWith("0b") || textString.startsWith("0B")) {
            val = new BigInteger(textString.substring(2), 2);
        }
        else if (textString.charAt(0) == '0') {
            val = new BigInteger(textString, 8);
        }
        else {
            val = new BigInteger(textString, 10);
        }
        String hexString = "0x" + val.toString(16);
        if (isLong) {
            hexString += 'L';
        }
        PsiImplUtil.replaceExpression(hexString, exp);
    }
}
