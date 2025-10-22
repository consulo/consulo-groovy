/*
 * Copyright 2007-2013 Dave Griffith, Bas Leijdekkers
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
package org.jetbrains.plugins.groovy.impl.codeInspection.control;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;

import java.util.regex.Pattern;

public class GroovyFallthroughInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Fall-through in switch statement");
    }

    @Nullable
    @Override
    protected String buildErrorString(Object... args) {
        return "Fall-through in switch statement #loc";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        private static final Pattern commentPattern = Pattern.compile("(?i)falls?\\s*thro?u");

        @Override
        @RequiredReadAction
        public void visitSwitchStatement(GrSwitchStatement switchStatement) {
            super.visitSwitchStatement(switchStatement);
            GrCaseSection[] caseSections = switchStatement.getCaseSections();
            for (int i = 1; i < caseSections.length; i++) {
                GrCaseSection caseSection = caseSections[i];
                if (isCommented(caseSection)) {
                    continue;
                }
                GrCaseSection previousCaseSection = caseSections[i - 1];
                GrStatement[] statements = previousCaseSection.getStatements();
                if (statements.length == 0) {
                    registerError(caseSection.getFirstChild());
                }
                else {
                    GrStatement lastStatement = statements[statements.length - 1];
                    if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
                        registerError(caseSection.getFirstChild());
                    }
                }
            }
        }

        @RequiredReadAction
        private static boolean isCommented(GrCaseSection caseClause) {
            PsiElement element = PsiTreeUtil.skipSiblingsBackward(caseClause, PsiWhiteSpace.class);
            return element instanceof PsiComment comment && commentPattern.matcher(comment.getText()).find();
        }
    }
}