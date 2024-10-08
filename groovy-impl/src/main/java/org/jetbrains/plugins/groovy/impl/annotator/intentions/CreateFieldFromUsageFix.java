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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.annotation.access.RequiredReadAction;
import consulo.groovy.localize.GroovyLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

/**
 * @author ven
 */
public class CreateFieldFromUsageFix extends GrCreateFromUsageBaseFix {
    @Nonnull
    private final String myReferenceName;

    public CreateFieldFromUsageFix(GrReferenceExpression refExpression, @Nonnull String referenceName) {
        super(refExpression);
        myReferenceName = referenceName;
    }

    @RequiredReadAction
    private String[] generateModifiers(@Nonnull PsiClass targetClass) {
        final GrReferenceExpression myRefExpression = getRefExpr();
        if (myRefExpression != null && GrStaticChecker.isInStaticContext(myRefExpression, targetClass)) {
            return new String[]{PsiModifier.STATIC};
        }
        return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @RequiredReadAction
    private TypeConstraint[] calculateTypeConstrains() {
        return GroovyExpectedTypesProvider.calculateTypeConstraints(getRefExpr());
    }

    @Override
    @Nonnull
    public String getText() {
        return GroovyLocalize.createFieldFromUsage(myReferenceName).get();
    }

    @Override
    @RequiredUIAccess
    protected void invokeImpl(Project project, @Nonnull PsiClass targetClass) {
        final CreateFieldFix fix = new CreateFieldFix(targetClass);
        fix.doFix(targetClass.getProject(), generateModifiers(targetClass), myReferenceName, calculateTypeConstrains(), getRefExpr());
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    protected boolean canBeTargetClass(PsiClass psiClass) {
        return super.canBeTargetClass(psiClass) && !psiClass.isInterface() && !psiClass.isAnnotationType();
    }
}
