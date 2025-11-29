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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.IElementType;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.editor.util.HighlightTypeUtil;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Max Medvedev
 */
public class GrInspectionUtil {
    @RequiredReadAction
    public static boolean isNull(@Nonnull GrExpression expression) {
        return "null".equals(expression.getText());
    }

    public static boolean isEquality(@Nonnull GrBinaryExpression binaryCondition) {
        IElementType tokenType = binaryCondition.getOperationTokenType();
        return GroovyTokenTypes.mEQUAL == tokenType;
    }

    public static boolean isInequality(@Nonnull GrBinaryExpression binaryCondition) {
        IElementType tokenType = binaryCondition.getOperationTokenType();
        return GroovyTokenTypes.mNOT_EQUAL == tokenType;
    }

    @RequiredReadAction
    public static HighlightInfo.Builder createAnnotationForRef(
        @Nonnull GrReferenceElement ref,
        @Nonnull HighlightDisplayLevel displayLevel,
        @Nonnull LocalizeValue message
    ) {
        PsiElement refNameElement = ref.getReferenceNameElement();
        assert refNameElement != null;

        if (displayLevel == HighlightDisplayLevel.ERROR) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refNameElement).descriptionAndTooltip(message);
        }

        if (displayLevel == HighlightDisplayLevel.WEAK_WARNING) {
            HighlightInfoType infoType = HighlightInfoType.INFORMATION;

            HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(infoType).range(refNameElement);
            builder.descriptionAndTooltip(message);
            return builder.needsUpdateOnTyping(false).textAttributes(GroovySyntaxHighlighter.UNRESOLVED_ACCESS);
        }

        HighlightInfoType highlightInfoType = HighlightTypeUtil.convertSeverity(displayLevel.getSeverity());
        return HighlightInfo.newHighlightInfo(highlightInfoType).range(refNameElement).descriptionAndTooltip(message);
    }
}
