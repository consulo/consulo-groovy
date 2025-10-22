/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.validity;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.HashSet;
import java.util.Set;

public class GroovyDuplicateSwitchBranchInspection extends BaseInspection {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return VALIDITY_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Duplicate switch case");
    }

    @Nullable
    @Override
    protected String buildErrorString(Object... args) {
        return "Duplicate switch case '#ref' #loc";
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitSwitchStatement(GrSwitchStatement grSwitchStatement) {
            super.visitSwitchStatement(grSwitchStatement);
            Set<GrExpression> duplicateExpressions = new HashSet<>();
            GrCaseLabel[] labels = collectCaseLabels(grSwitchStatement);
            for (int i = 0; i < labels.length; i++) {
                GrCaseLabel label1 = labels[i];
                GrExpression expression1 = getExpressionForCaseLabel(label1);
                for (int j = i + 1; j < labels.length; j++) {
                    GrCaseLabel label2 = labels[j];
                    GrExpression expression2 = getExpressionForCaseLabel(label2);
                    if (EquivalenceChecker.expressionsAreEquivalent(expression1, expression2)) {
                        duplicateExpressions.add(expression1);
                        duplicateExpressions.add(expression2);
                    }
                }
            }
            for (GrExpression duplicateExpression : duplicateExpressions) {
                registerError(duplicateExpression);
            }
        }
    }

    private static GrCaseLabel[] collectCaseLabels(final GrSwitchStatement containingStatelent) {
        final Set<GrCaseLabel> labels = new HashSet<>();
        GroovyRecursiveElementVisitor visitor = new GroovyRecursiveElementVisitor() {
            @Override
            public void visitCaseLabel(GrCaseLabel grCaseLabel) {
                super.visitCaseLabel(grCaseLabel);
                labels.add(grCaseLabel);
            }

            @Override
            public void visitSwitchStatement(GrSwitchStatement grSwitchStatement) {
                if (containingStatelent.equals(grSwitchStatement)) {
                    super.visitSwitchStatement(grSwitchStatement);
                }
            }
        };
        containingStatelent.accept(visitor);
        return labels.toArray(new GrCaseLabel[labels.size()]);
    }

    @Nullable
    @RequiredReadAction
    private static GrExpression getExpressionForCaseLabel(GrCaseLabel label) {
        for (PsiElement child : label.getChildren()) {
            if (child instanceof GrExpression expr) {
                return expr;
            }
        }
        return null;
    }
}