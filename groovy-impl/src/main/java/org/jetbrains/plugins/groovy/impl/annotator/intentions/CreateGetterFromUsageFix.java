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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Max Medvedev
 */
public class CreateGetterFromUsageFix extends CreateMethodFromUsageFix {
    public CreateGetterFromUsageFix(@Nonnull GrReferenceExpression refExpression, @Nonnull PsiClass targetClass) {
        super(refExpression);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected TypeConstraint[] getReturnTypeConstraints() {
        return GroovyExpectedTypesProvider.calculateTypeConstraints(getRefExpr());
    }

    @Override
    @RequiredReadAction
    protected PsiType[] getArgumentTypes() {
        return PsiType.EMPTY_ARRAY;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected String getMethodName() {
        return GroovyPropertyUtils.getGetterNameNonBoolean(getRefExpr().getReferenceName());
    }
}
