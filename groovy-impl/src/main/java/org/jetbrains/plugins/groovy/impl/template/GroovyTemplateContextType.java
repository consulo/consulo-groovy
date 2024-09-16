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
package org.jetbrains.plugins.groovy.impl.template;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public abstract class GroovyTemplateContextType extends BaseTemplateContextType {

    protected GroovyTemplateContextType(@Nonnull String id,
                                        @Nonnull LocalizeValue presentableName,
                                        @Nullable Class<? extends TemplateContextType> baseContextType) {
        super(id, presentableName, baseContextType);
    }

    @Override
    public boolean isInContext(@Nonnull final PsiFile file, final int offset) {
        if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(GroovyFileType.GROOVY_LANGUAGE)) {
            PsiElement element = file.findElementAt(offset);
            if (element instanceof PsiWhiteSpace) {
                return false;
            }
            return element != null && isInContext(element);
        }

        return false;
    }

    @RequiredReadAction
    protected abstract boolean isInContext(@Nonnull PsiElement element);

    @RequiredReadAction
    protected static boolean isAfterExpression(PsiElement element) {
        ProcessingContext context = new ProcessingContext();
        if (PlatformPatterns.psiElement().afterLeaf(
            PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(GrExpression.class).save("prevExpr"))).accepts(element, context)) {
            PsiElement prevExpr = (PsiElement) context.get("prevExpr");
            if (prevExpr.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) {
                return true;
            }
        }
        return false;
    }

}
