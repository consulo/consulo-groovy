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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GrTypeConverter {

  public static final ExtensionPointName<GrTypeConverter> EP_NAME = ExtensionPointName.create(GrTypeConverter.class);

  protected static boolean isMethodCallConversion(GroovyPsiElement context) {
    return PsiUtil.isInMethodCallContext(context);
  }

  @Nullable
  protected static GrLiteral getLiteral(@Nonnull GroovyPsiElement context) {
    GrExpression expression;
    if (context instanceof GrTypeCastExpression) {
      expression = ((GrTypeCastExpression)context).getOperand();
    }
    else if (context instanceof GrAssignmentExpression) {
      expression = ((GrAssignmentExpression)context).getRValue();
    }
    else if (context instanceof GrVariable) {
      expression = ((GrVariable)context).getInitializerGroovy();
    }
    else if (context instanceof GrReturnStatement) {
      expression = ((GrReturnStatement)context).getReturnValue();
    }
    else {
      expression = context instanceof GrExpression ? (GrExpression)context : null;
    }
    return expression instanceof GrLiteral ? (GrLiteral)expression : null;
  }

  /**
   * @deprecated see {@link #isApplicableTo(GrTypeConverter.ApplicableTo)}
   */
  @Deprecated
  public boolean isAllowedInMethodCall() {
    return false;
  }

  @SuppressWarnings("deprecation")
  public boolean isApplicableTo(@Nonnull ApplicableTo position) {
    switch (position) {
      case EXPLICIT_CAST:
        return false;
      case ASSIGNMENT:
        return true;
      case METHOD_PARAMETER:
        return isAllowedInMethodCall();
      case RETURN_VALUE:
        return true;
      default:
        return false;
    }
  }

  /**
   * @deprecated see {@link #isConvertibleEx(PsiType, PsiType, GroovyPsiElement, GrTypeConverter.ApplicableTo)}
   */
  @Deprecated
  @Nullable
  public Boolean isConvertible(@Nonnull PsiType lType, @Nonnull PsiType rType, @Nonnull GroovyPsiElement context) {
    return null;
  }

  /**
   * Checks if {@code actualType} can be converted to {@code targetType}.
   *
   * @param targetType target type
   * @param actualType actual type
   * @param context    context
   * @return {@link ConversionResult conversion result }
   */
  @SuppressWarnings("deprecation")
  @Nullable
  public ConversionResult isConvertibleEx(@Nonnull PsiType targetType,
                                          @Nonnull PsiType actualType,
                                          @Nonnull GroovyPsiElement context,
                                          @Nonnull ApplicableTo currentPosition) {
    Boolean result = isConvertible(targetType, actualType, context);
    return result == null ? null
                          : result ? ConversionResult.OK
                                   : ConversionResult.ERROR;
  }

  public enum ApplicableTo {
    EXPLICIT_CAST,
    ASSIGNMENT,
    METHOD_PARAMETER,
    RETURN_VALUE
  }
}
