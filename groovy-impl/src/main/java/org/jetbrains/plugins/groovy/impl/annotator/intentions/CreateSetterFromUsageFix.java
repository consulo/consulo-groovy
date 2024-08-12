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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.intention.LowPriorityAction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class CreateSetterFromUsageFix extends CreateMethodFromUsageFix implements LowPriorityAction {
    public CreateSetterFromUsageFix(@Nonnull GrReferenceExpression refExpression) {
        super(refExpression);
    }

    @Nonnull
    @Override
    protected TypeConstraint[] getReturnTypeConstraints() {
        return new TypeConstraint[]{SubtypeConstraint.create(PsiType.VOID)};
    }

    @Override
    protected PsiType[] getArgumentTypes() {
        final GrReferenceExpression ref = getRefExpr();
        assert PsiUtil.isLValue(ref);
        PsiType initializer = TypeInferenceHelper.getInitializerTypeFor(ref);
        if (initializer == null || initializer == PsiType.NULL) {
            initializer = TypesUtil.getJavaLangObject(ref);
        }
        return new PsiType[]{initializer};
    }

    @Nonnull
    @Override
    protected String getMethodName() {
        return GroovyPropertyUtils.getSetterName(getRefExpr().getReferenceName());
    }
}
