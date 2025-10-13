/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyLabeledStatementInspection extends BaseInspection {
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new MyVisitor();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PROBABLE_BUGS;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected String buildErrorString(Object... args) {
        return GroovyLocalize.labelAlreadyUsed(args).get();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.checkLabeledStatement();
    }

    private static class MyVisitor extends BaseInspectionVisitor {
        @Override
        public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
            super.visitLabeledStatement(labeledStatement);

            final String name = labeledStatement.getName();
            GrLabeledStatement existing = ResolveUtil.resolveLabeledStatement(name, labeledStatement, true);
            if (existing != null && PsiTreeUtil.isAncestor(existing, labeledStatement, true)) {
                registerError(labeledStatement.getLabel(), name);
            }
        }
    }
}
