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
package org.jetbrains.plugins.groovy.impl.griffon;

import com.intellij.java.language.psi.PsiAnnotationOwner;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.impl.annotator.checkers.CustomAnnotationChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GriffonPropertyListenerAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    if (!"griffon.transform.PropertyListener".equals(annotation.getQualifiedName())) {
      return false;
    }

    final GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length != 1) {
      return false;
    }

    final GrAnnotationNameValuePair attribute = attributes[0];
    final GrAnnotationMemberValue value = attribute.getValue();

    final PsiAnnotationOwner owner = annotation.getOwner();
    if (owner instanceof GrField) {
      if (value instanceof GrClosableBlock) {
        return true;
      }
    }
    else if (owner instanceof GrTypeDefinition) {
      if (value instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)value).resolve();
        if (resolved instanceof GrField) {
          final PsiClass containingClass = ((GrField)resolved).getContainingClass();
          if (annotation.getManager().areElementsEquivalent((PsiElement)owner, containingClass)) {
            return true;
          }
        }
      }
    }

    return false;
  }
}
