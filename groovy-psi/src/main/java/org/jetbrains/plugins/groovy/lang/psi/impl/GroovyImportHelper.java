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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.resolve.DefaultImportContributor;
import org.jetbrains.plugins.groovy.lang.resolve.PackageSkippingProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.LinkedHashSet;

/**
 * @author Max Medvedev
 */
public class GroovyImportHelper {
  public static boolean isImplicitlyImported(PsiElement element, String expectedName, GroovyFile file) {
    if (!(element instanceof PsiClass)) {
      return false;
    }

    final PsiClass psiClass = (PsiClass)element;
    if (!expectedName.equals(psiClass.getName())) {
      return false;
    }

    final String qname = psiClass.getQualifiedName();
    if (qname == null) {
      return false;
    }

    for (String importedClass : GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES) {
      if (qname.equals(importedClass)) {
        return true;
      }
    }
    for (String pkg : getImplicitlyImportedPackages(file)) {
      if (qname.equals(pkg + "." + expectedName) || pkg.isEmpty() && qname.equals(expectedName)) {
        return true;
      }
    }
    return false;
  }

  public static LinkedHashSet<String> getImplicitlyImportedPackages(@Nonnull GroovyFile file) {
    final LinkedHashSet<String> result = new LinkedHashSet<String>();
    ContainerUtil.addAll(result, GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES);

    for (DefaultImportContributor contributor : DefaultImportContributor.EP_NAME.getExtensions()) {
      result.addAll(contributor.appendImplicitlyImportedPackages(file));
    }

    return result;
  }

  public static boolean processImports(@Nonnull ResolveState state,
                                       @Nullable PsiElement lastParent,
                                       @Nonnull PsiElement place,
                                       @Nonnull PsiScopeProcessor importProcessor,
                                       @Nonnull GrImportStatement[] importStatements,
                                       boolean shouldProcessOnDemand) {
    for (int i = importStatements.length - 1; i >= 0; i--) {
      final GrImportStatement imp = importStatements[i];
      if (shouldProcessOnDemand != imp.isOnDemand()) {
        continue;
      }
      if (!imp.processDeclarations(importProcessor, state, lastParent, place)) {
        return false;
      }
    }
    return true;
  }

  public static boolean processImplicitImports(@Nonnull PsiScopeProcessor processor,
                                               @Nonnull ResolveState state,
                                               @Nullable PsiElement lastParent,
                                               @Nonnull PsiElement place,
                                               @Nonnull GroovyFile file) {
    if (!ResolveUtil.shouldProcessClasses(processor.getHint(ClassHint.KEY))) {
      return true;
    }

    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());

    final PsiScopeProcessor packageSkipper = new PackageSkippingProcessor(processor);

    for (final String implicitlyImported : getImplicitlyImportedPackages(file)) {
      PsiPackage aPackage = facade.findPackage(implicitlyImported);
      if (aPackage == null) {
        continue;
      }

      if (!aPackage.processDeclarations(packageSkipper, state, lastParent, place)) {
        return false;
      }
    }

    GroovyPsiManager groovyPsiManager = GroovyPsiManager.getInstance(file.getProject());
    for (String implicitlyImportedClass : GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES) {
      PsiClass clazz = groovyPsiManager.findClassWithCache(implicitlyImportedClass, file.getResolveScope());
      if (clazz != null && !ResolveUtil.processElement(processor, clazz, state)) {
        return false;
      }
    }
    return true;
  }
}
