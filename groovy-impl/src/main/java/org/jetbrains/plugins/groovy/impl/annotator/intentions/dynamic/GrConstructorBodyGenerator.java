/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic;

import com.intellij.java.impl.codeInsight.generation.ConstructorBodyGenerator;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrConstructorBodyGenerator implements ConstructorBodyGenerator {
  @Override
  public void generateFieldInitialization(@Nonnull StringBuilder buffer,
                                          @Nonnull PsiField[] fields,
                                          @Nonnull PsiParameter[] parameters) {
    for (int i = 0, length = fields.length; i < length; i++) {
      String fieldName = fields[i].getName();
      String paramName = parameters[i].getName();
      if (fieldName.equals(paramName)) {
        buffer.append("this.");
      }
      buffer.append(fieldName);
      buffer.append("=");
      buffer.append(paramName);
      buffer.append("\n");
    }
  }

  @Override
  public void generateSuperCallIfNeeded(@Nonnull StringBuilder buffer, @Nonnull PsiParameter[] parameters) {
    if (parameters.length > 0) {
      buffer.append("super(");
      for (int j = 0; j < parameters.length; j++) {
        PsiParameter param = parameters[j];
        buffer.append(param.getName());
        if (j < parameters.length - 1) buffer.append(",");
      }
      buffer.append(")\n");
    }
  }

  @Override
  public StringBuilder start(StringBuilder buffer, @Nonnull String name, @Nonnull PsiParameter[] parameters) {
    buffer.append("public ").append(name).append("(");
    for (PsiParameter parameter : parameters) {
      buffer.append(parameter.getType().getPresentableText()).append(' ').append(parameter.getName()).append(',');
    }
    if (parameters.length > 0) {
      buffer.delete(buffer.length() - 1, buffer.length());
    }
    buffer.append("){\n");
    return buffer;
  }

  @Override
  public void finish(StringBuilder buffer) {
    buffer.append('}');
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
