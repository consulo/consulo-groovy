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
package org.jetbrains.plugins.groovy.impl.lang.psi.impl.statements.expressions.path;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.List;

/**
 * Created by Max Medvedev on 21/05/14
 */
public class GrWithTraitTypeCalculator extends GrCallExpressionTypeCalculator {
  @Nullable
  @Override
  protected PsiType calculateReturnType(@Nonnull GrMethodCall callExpression, @Nonnull PsiMethod resolvedMethod) {
    if (!"withTraits".equals(resolvedMethod.getName())) return null;

    if (resolvedMethod instanceof GrGdkMethod) {
      resolvedMethod = ((GrGdkMethod)resolvedMethod).getStaticMethod();
    }

    GrExpression invokedExpression = callExpression.getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression)) return null;
    GrExpression originalObject = ((GrReferenceExpression)invokedExpression).getQualifierExpression();
    if (originalObject == null) return null;

    PsiType invokedType = originalObject.getType();
    if (!(invokedType instanceof PsiClassType)) return null;

    PsiClass containingClass = resolvedMethod.getContainingClass();
    if (containingClass == null || !GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName())) return null;

    List<PsiClassType> traits = ContainerUtil.newArrayList();
    GrExpression[] args = callExpression.getArgumentList().getExpressionArguments();
    for (GrExpression arg : args) {
      PsiType type = arg.getType();
      PsiType classItem = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_LANG_CLASS, 0, false);
      PsiClass psiClass = PsiTypesUtil.getPsiClass(classItem);
      if (GrTraitUtil.isTrait(psiClass)) {
        traits.add((PsiClassType)classItem);
      }
    }

    return GrTraitType.createTraitClassType(callExpression, (PsiClassType)invokedType, traits, callExpression.getResolveScope());
  }
}
