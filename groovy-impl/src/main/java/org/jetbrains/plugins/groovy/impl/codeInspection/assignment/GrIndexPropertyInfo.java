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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 05/02/14
 */
public class GrIndexPropertyInfo extends CallInfoBase<GrIndexProperty> {
  public GrIndexPropertyInfo(GrIndexProperty call) {
    super(call);
  }

  @Nullable
  @Override
  protected PsiType[] inferArgTypes() {
    return PsiUtil.getArgumentTypes(getCall().getInvokedExpression(), true);
  }

  @Nonnull
  @Override
  public GrExpression getInvokedExpression() {
    return getCall().getInvokedExpression();
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return getInvokedExpression().getType();
  }

  @Nonnull
  @Override
  public PsiElement getHighlightElementForCategoryQualifier() {
    GrExpression invoked = getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) {
      PsiElement refNameElement = ((GrReferenceExpression)invoked).getReferenceNameElement();
      if (refNameElement != null) {
        return refNameElement;
      }
    }

    return invoked;
  }

  @Nonnull
  @Override
  public PsiElement getElementToHighlight() {
    return getCall().getArgumentList();
  }
}
