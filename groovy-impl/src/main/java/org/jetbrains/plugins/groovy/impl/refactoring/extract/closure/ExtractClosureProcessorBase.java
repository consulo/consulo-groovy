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
package org.jetbrains.plugins.groovy.impl.refactoring.extract.closure;

import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.GrIntroduceParameterSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

/**
 * @author Max Medvedev
 */
public abstract class ExtractClosureProcessorBase extends BaseRefactoringProcessor {
    protected final GrIntroduceParameterSettings myHelper;
    private static final LocalizeValue EXTRACT_CLOSURE = LocalizeValue.localizeTODO("Extract closure");

    public ExtractClosureProcessorBase(@Nonnull GrIntroduceParameterSettings helper) {
        super(helper.getProject());
        myHelper = helper;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new UsageViewDescriptorAdapter() {
            @Nonnull
            @Override
            public PsiElement[] getElements() {
                return new PsiElement[]{myHelper.getToSearchFor()};
            }

            @Override
            public String getProcessedElementsHeader() {
                return EXTRACT_CLOSURE.get();
            }
        };
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return EXTRACT_CLOSURE;
    }

    public static GrClosableBlock generateClosure(GrIntroduceParameterSettings helper) {
        StringBuilder buffer = new StringBuilder();

        buffer.append("{ ");

        String[] params = ExtractUtil.getParameterString(helper, true);
        if (params.length > 0) {
            for (String p : params) {
                buffer.append(p);
            }
            buffer.append("->");
        }
        if (helper.getStatements().length > 1) {
            buffer.append('\n');
        }

        ExtractUtil.generateBody(helper, false, buffer, helper.isForceReturn());
        buffer.append(" }");

        return GroovyPsiElementFactory.getInstance(helper.getProject()).createClosureFromText(buffer.toString(), helper.getToReplaceIn());
    }
}
