/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrFieldAlreadyDefinedInspection extends BaseInspection {
  @Nonnull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }


  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }


  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("field.already.defined", args);
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("field.already.defined");
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitField(GrField field) {
      super.visitField(field);

      PsiElement duplicate = ResolveUtil.findDuplicate(field);
      if (duplicate instanceof GrField) {
        registerError(field.getNameIdentifierGroovy(), field.getName());
      }
    }
  }
}
