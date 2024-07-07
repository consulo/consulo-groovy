/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.template;

import com.intellij.java.impl.codeInsight.template.macro.VariableTypeCalculator;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyVariableTypeCalculator extends VariableTypeCalculator {
  @Override
  public PsiType inferVarTypeAt(@Nonnull PsiVariable var, @Nonnull PsiElement place) {
    if (!(var instanceof GrVariable) || !(place.getLanguage() == GroovyFileType.GROOVY_LANGUAGE)) return null;
    if (var instanceof GrField) return var.getType();

    return TypeInferenceHelper.getInferredType(place, var.getName());
  }
}
