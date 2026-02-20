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
package org.jetbrains.plugins.groovy.impl.codeInspection.exception;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInsight.daemon.impl.quickfix.RenameElementFix;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

@ExtensionImpl
public class GroovyEmptyCatchBlockInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return ERROR_HANDLING;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Empty 'catch' block");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        public void visitCatchClause(GrCatchClause catchClause) {
            super.visitCatchClause(catchClause);
            GrOpenBlock body = catchClause.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }

            GrParameter parameter = catchClause.getParameter();
            if (parameter == null) {
                return;
            }
            if (GrExceptionUtil.ignore(parameter)) {
                return;
            }

            problemsHolder.newProblem(LocalizeValue.localizeTODO("Empty '#ref' block #loc"))
                .range(catchClause.getFirstChild())
                .withFixes(new RenameElementFix(parameter, "ignored"))
                .create();
        }

        private static boolean isEmpty(@Nonnull GrOpenBlock body) {
            GrStatement[] statements = body.getStatements();
            return statements.length == 0;
        }
    }
}