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
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ven
 */
public interface GroovyFile extends GroovyFileBase {
  GroovyFile[] EMPTY_ARRAY = new GroovyFile[0];

  GrImportStatement[] getImportStatements();

  @Nonnull
  String getPackageName();

  @Nullable
  GrPackageDefinition getPackageDefinition();

  void setPackageName(String packageName);

  @Nullable
  GrPackageDefinition setPackage(GrPackageDefinition newPackage);

  @Nullable
  PsiType getInferredScriptReturnType();
}
