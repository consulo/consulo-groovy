/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.naming;

import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

public class GroovyConstantNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Nonnull
  public String getDisplayName() {
    return "Constant naming convention";
  }

  protected GroovyFix buildFix(PsiElement location) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... args) {
    final String className = (String) args[0];
    if (className.length() < getMinLength()) {
      return "Constant name '#ref' is too short";
    } else if (className.length() > getMaxLength()) {
      return "Constant name '#ref' is too long";
    }
    return "Constant name '#ref' doesn't match regex '" + getRegex() + "' #loc";
  }

  protected String getDefaultRegex() {
    return "[A-Z\\d]*";
  }

  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }


  private class NamingConventionsVisitor extends BaseInspectionVisitor {
    public void visitField(GrField grField) {
      super.visitField(grField);
      if (!grField.hasModifierProperty(PsiModifier.STATIC) ||
          !grField.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final String name = grField.getName();
      if (isValid(name)) {
        return;
      }
      registerVariableError(grField, name);
    }
  }
}