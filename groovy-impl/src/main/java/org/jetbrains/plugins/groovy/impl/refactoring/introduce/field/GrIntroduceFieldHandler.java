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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.field;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.java.language.psi.PsiClass;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceFieldHandlerBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldHandler extends GrIntroduceFieldHandlerBase<GrIntroduceFieldSettings> {
    @Nonnull
    @Override
    protected LocalizeValue getRefactoringName() {
        return IntroduceFieldHandler.REFACTORING_NAME;
    }

    @Nonnull
    @Override
    protected String getHelpID() {
        return HelpID.INTRODUCE_FIELD;
    }

    @Override
    protected void checkExpression(@Nonnull GrExpression selectedExpr) {
        checkContainingClass(selectedExpr);
    }

    private static void checkContainingClass(PsiElement place) {
        PsiClass containingClass = PsiUtil.getContextClass(place);
        if (containingClass == null) {
            throw new GrRefactoringError(GroovyRefactoringLocalize.cannotIntroduceFieldInScript().get());
        }
        if (containingClass.isInterface()) {
            throw new GrRefactoringError(GroovyRefactoringLocalize.cannotIntroduceFieldInInterface().get());
        }
        if (PsiUtil.skipParentheses(place, false) == null) {
            throw new GrRefactoringError(GroovyRefactoringLocalize.expressionContainsErrors().get());
        }
    }

    @Override
    protected void checkVariable(@Nonnull GrVariable variable) throws GrRefactoringError {
        checkContainingClass(variable);
    }

    @Override
    protected void checkStringLiteral(@Nonnull StringPartInfo info) throws GrRefactoringError {
        checkContainingClass(info.getLiteral());
    }

    @Override
    protected void checkOccurrences(@Nonnull PsiElement[] occurrences) {
        //nothing to do
    }

    @Nonnull
    @Override
    protected GrIntroduceDialog<GrIntroduceFieldSettings> getDialog(@Nonnull GrIntroduceContext context) {
        return new GrIntroduceFieldDialog(context);
    }

    @Override
    public GrVariable runRefactoring(@Nonnull GrIntroduceContext context, @Nonnull GrIntroduceFieldSettings settings) {
        return new GrIntroduceFieldProcessor(context, settings).run();
    }

    @Override
    @RequiredUIAccess
    protected GrAbstractInplaceIntroducer<GrIntroduceFieldSettings> getIntroducer(
        @Nonnull GrIntroduceContext context,
        OccurrencesChooser.ReplaceChoice choice
    ) {
        SimpleReference<GrIntroduceContext> contextRef = SimpleReference.create(context);

        if (context.getStringPart() != null) {
            extractStringPart(contextRef);
        }

        return new GrInplaceFieldIntroducer(contextRef.get(), choice);
    }

    @Nonnull
    @Override
    protected PsiElement[] findOccurrences(@Nonnull GrExpression expression, @Nonnull PsiElement scope) {
        PsiElement[] occurrences = super.findOccurrences(expression, scope);
        if (shouldBeStatic(expression, scope)) {
            return occurrences;
        }

        List<PsiElement> filtered = new ArrayList<>();
        for (PsiElement occurrence : occurrences) {
            if (!shouldBeStatic(occurrence, scope)) {
                filtered.add(occurrence);
            }
        }
        return filtered.toArray(new PsiElement[filtered.size()]);
    }

    @Nullable
    static GrMember getContainer(@Nullable PsiElement place, @Nullable PsiElement scope) {
        while (place != null && place != scope) {
            place = place.getParent();
            if (place instanceof GrMember) {
                return (GrMember) place;
            }
        }
        return null;
    }

    static boolean shouldBeStatic(PsiElement expr, PsiElement clazz) {
        GrMember method = getContainer(expr, clazz);
        return method != null && method.isStatic();
    }
}
