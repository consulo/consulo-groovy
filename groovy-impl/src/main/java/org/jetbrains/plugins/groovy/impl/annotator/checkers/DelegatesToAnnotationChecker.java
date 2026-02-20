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
package org.jetbrains.plugins.groovy.impl.annotator.checkers;

import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiAnnotationOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;

/**
 * checks the following case:
 * <p/>
 * def foo(@DelegatesTo.Target def targetArg, @DelegatesTo Closure cl) {}
 *
 * @author Max Medvedev
 */
@ExtensionImpl
public class DelegatesToAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    if (!GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO.equals(annotation.getQualifiedName())) {
      return false;
    }

    PsiAnnotationMemberValue valueAttribute = annotation.findAttributeValue("value");

    if (valueAttribute == null) {
      PsiAnnotationOwner owner = annotation.getOwner();
      if (owner instanceof GrModifierList) {
        PsiElement parent1 = ((GrModifierList)owner).getParent();
        if (parent1 instanceof GrParameter) {
          PsiElement parent = parent1.getParent();
          if (parent instanceof GrParameterList) {
            for (GrParameter parameter : ((GrParameterList)parent).getParameters()) {
              if (parameter.getModifierList().findAnnotation(GroovyCommonClassNames
                                                               .GROOVY_LANG_DELEGATES_TO_TARGET) != null) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }
}
