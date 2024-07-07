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
package org.jetbrains.plugins.groovy.impl.dgm;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class DGMMemberContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@Nonnull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    Project project = place.getProject();
    GlobalSearchScope resolveScope = place.getResolveScope();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    Pair<List<String>, List<String>> extensions = GroovyExtensionProvider.getInstance(project).collectExtensions(resolveScope);

    List<String> instanceCategories = extensions.getFirst();
    List<String> staticCategories = extensions.getSecond();

    if (!processCategories(qualifierType, processor, state, project, resolveScope, facade, instanceCategories, false)) return;
    if (!processCategories(qualifierType, processor, state, project, resolveScope, facade, staticCategories, true)) return;
  }

  private static boolean processCategories(PsiType qualifierType,
                                           PsiScopeProcessor processor,
                                           ResolveState state,
                                           Project project,
                                           GlobalSearchScope resolveScope,
                                           JavaPsiFacade facade,
                                           List<String> instanceCategories,
                                           boolean isStatic) {
    for (String category : instanceCategories) {
      PsiClass clazz = facade.findClass(category, resolveScope);
      if (clazz != null) {
        if (!GdkMethodHolder.getHolderForClass(clazz, isStatic, resolveScope).processMethods(processor, state, qualifierType, project)) {
          return false;
        }
      }
    }
    return true;
  }
}
