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

package org.jetbrains.plugins.groovy.lang.psi.impl.javaView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.Collection;
import java.util.List;

/**
 * @author ven
 */
public class GroovyClassFinder extends PsiElementFinder {
  private final GroovyShortNamesCache myCache;

  public GroovyClassFinder(Project project) {
    myCache = GroovyShortNamesCache.getGroovyShortNamesCache(project);
  }

  @javax.annotation.Nullable
  public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
    final List<PsiClass> classes = myCache.getScriptClassesByFQName(qualifiedName, scope, true);
    return classes.isEmpty() ? null : classes.get(0);
  }

  @Nonnull
  public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
    final Collection<PsiClass> classes = myCache.getScriptClassesByFQName(qualifiedName, scope, true);
    return classes.isEmpty() ? PsiClass.EMPTY_ARRAY : classes.toArray(new PsiClass[classes.size()]);
  }

}
