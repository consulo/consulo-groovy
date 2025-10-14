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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public class GrDeprecatedAPIUsageInspection extends BaseInspection {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.grDeprecatedApiUsage();
    }

    @NonNls
    @Nonnull
    public String getShortName() {
        return "GrDeprecatedAPIUsage";
    }

    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            public void visitReferenceExpression(GrReferenceExpression ref) {
                super.visitReferenceExpression(ref);
                checkRef(ref);
            }

            @Override
            public void visitCodeReferenceElement(GrCodeReferenceElement ref) {
                super.visitCodeReferenceElement(ref);
                checkRef(ref);
            }

            private void checkRef(GrReferenceElement ref) {
                PsiElement resolved = ref.resolve();
                if (isDeprecated(resolved)) {
                    PsiElement toHighlight = getElementToHighlight(ref);
                    problemsHolder.newProblem(GroovyLocalize.zeroIsDeprecated(ref.getReferenceName()))
                        .range(toHighlight)
                        .highlightType(ProblemHighlightType.LIKE_DEPRECATED)
                        .create();
                }
            }

            @Nonnull
            public PsiElement getElementToHighlight(@Nonnull GrReferenceElement refElement) {
                final PsiElement refNameElement = refElement.getReferenceNameElement();
                return refNameElement != null ? refNameElement : refElement;
            }


            private boolean isDeprecated(PsiElement resolved) {
                if (resolved instanceof PsiDocCommentOwner) {
                    return ((PsiDocCommentOwner) resolved).isDeprecated();
                }
                if (resolved instanceof PsiModifierListOwner && PsiImplUtil.isDeprecatedByAnnotation((PsiModifierListOwner) resolved)) {
                    return true;
                }
                return false;
            }
        };
    }
}
