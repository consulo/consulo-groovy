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
package org.jetbrains.plugins.groovy.impl.codeInspection.threading;

import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class GroovySynchronizationOnVariableInitializedWithLiteralInspection extends BaseInspection {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return THREADING_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Synchronization on variable initialized with literal");
    }

    @Nullable
    @Override
    protected String buildErrorString(Object... args) {
        return "Synchronization on variable '#ref', which was initialized with a literal #loc";
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
            super.visitSynchronizedStatement(synchronizedStatement);
            if (!(synchronizedStatement.getMonitor() instanceof GrReferenceExpression lock)) {
                return;
            }
            PsiElement referent = lock.resolve();
            if (referent instanceof GrVariable variable) {
                if (variable.getInitializerGroovy() instanceof GrLiteral) {
                    registerError(lock);
                }
            }
            else if (referent instanceof PsiVariable variable) {
                if (variable.getInitializer() instanceof PsiLiteralExpression) {
                    registerError(lock);
                }
            }
        }
    }
}