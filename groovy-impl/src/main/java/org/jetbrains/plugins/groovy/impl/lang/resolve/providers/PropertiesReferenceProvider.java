/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.resolve.providers;

import com.intellij.lang.properties.references.PropertyReference;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author ven
 */
public class PropertiesReferenceProvider extends PsiReferenceProvider {
  public PropertiesReferenceProvider() {
  }

  @Nonnull
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
    Object value = null;

    if (element instanceof GrLiteral) {
      GrLiteral literalExpression = (GrLiteral) element;
      value = literalExpression.getValue();

      //final Map<String, Object> annotationParams = new HashMap<String, Object>();
      //annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
      /*if (JavaI18nUtil.mustBePropertyKey(literalExpression, annotationParams)) {
        soft = false;
        final Object resourceBundleName = annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
        if (resourceBundleName instanceof PsiExpression) {
          PsiExpression expr = (PsiExpression)resourceBundleName;
          final Object bundleValue = expr.getManager().getConstantEvaluationHelper().computeConstantExpression(expr);
          bundleName = bundleValue == null ? null : bundleValue.toString();
        }
      }*/
    }

    if (value instanceof String && !((String)value).contains("\n")) {
      return new PsiReference[]{new PropertyReference((String) value, element, null, true)};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  //public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  //}

}
