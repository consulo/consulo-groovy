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

package org.jetbrains.plugins.groovy.impl.lang.stubs;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.PsiShortNameProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.CommonProcessors;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.stub.StubIndex;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.*;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyShortNamesCache implements PsiShortNameProvider {
  private final Project myProject;

  @Inject
  public GroovyShortNamesCache(Project project) {
    myProject = project;
  }

  public static GroovyShortNamesCache getGroovyShortNamesCache(Project project) {
    return project.getExtensionPoint(PsiShortNameProvider.class).findExtensionOrFail(GroovyShortNamesCache.class);
  }

  @Override
  @Nonnull
  public PsiClass[] getClassesByName(@Nonnull @NonNls String name, @Nonnull GlobalSearchScope scope) {
    Collection<PsiClass> allClasses = new SmartList<PsiClass>();
    processClassesWithName(name, new CommonProcessors.CollectProcessor<PsiClass>(allClasses), scope, null);
    if (allClasses.isEmpty()) return PsiClass.EMPTY_ARRAY;
    return allClasses.toArray(new PsiClass[allClasses.size()]);
  }

  public List<PsiClass> getScriptClassesByFQName(final String name, final GlobalSearchScope scope, final boolean srcOnly) {
    GlobalSearchScope actualScope = srcOnly ? new GrSourceFilterScope(scope) : scope;
    final Collection<GroovyFile> files = StubIndex.getInstance().get(GrFullScriptNameIndex.KEY, name.hashCode(), myProject, actualScope);
    if (files.isEmpty()) {
      return Collections.emptyList();
    }

    final ArrayList<PsiClass> result = new ArrayList<PsiClass>();
    for (GroovyFile file : files) {
      if (file.isScript()) {
        final PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null && name.equals(scriptClass.getQualifiedName())) {
          result.add(scriptClass);
        }
      }
    }
    return result;
  }

  @Nonnull
  public List<PsiClass> getClassesByFQName(@Nonnull @NonNls String name, @Nonnull GlobalSearchScope scope) {
    final List<PsiClass> result = addClasses(name, scope, true);
    if (result.isEmpty()) {
      result.addAll(addClasses(name, scope, false));
    }
    if (result.isEmpty()) {
      result.addAll(addClasses(name, GlobalSearchScope.projectScope(myProject), false));
    }
    return result;
  }

  private List<PsiClass> addClasses(String name, GlobalSearchScope scope, boolean inSource) {
    final List<PsiClass> result = new ArrayList<PsiClass>(getScriptClassesByFQName(name, scope, inSource));

    for (PsiElement psiClass : StubIndex.getInstance().safeGet(GrFullClassNameIndex.KEY, name.hashCode(), myProject,
                                                               inSource ? new GrSourceFilterScope(scope) : scope, PsiClass.class)) {
      //hashcode doesn't guarantee equals
      if (name.equals(((PsiClass)psiClass).getQualifiedName())) {
        result.add((PsiClass)psiClass);
      }
    }
    return result;
  }

  @Override
  @Nonnull
  public String[] getAllClassNames() {
    return ArrayUtil.toStringArray(StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject));
  }


  @Override
  public void getAllClassNames(@Nonnull HashSet<String> dest) {
    dest.addAll(StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject));
  }

  @Override
  @Nonnull
  public PsiMethod[] getMethodsByName(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope) {
    final Collection<? extends PsiMethod> methods =
      StubIndex.getInstance().get(GrMethodNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
    final Collection<? extends PsiMethod> annMethods =
      StubIndex.getInstance().get(GrAnnotationMethodNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
    if (methods.isEmpty() && annMethods.isEmpty()) return PsiMethod.EMPTY_ARRAY;
    return ArrayUtil.mergeCollections(annMethods, methods, PsiMethod.ARRAY_FACTORY);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @Nonnull String name,
                                        @Nonnull GlobalSearchScope scope,
                                        @Nonnull Processor<PsiMethod> processor) {
    return processMethodsWithName(name, processor, scope, null);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @Nonnull String name,
                                        @Nonnull Processor<? super PsiMethod> processor,
                                        @Nonnull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    GrSourceFilterScope filterScope = new GrSourceFilterScope(scope);
    return StubIndex.getInstance()
                    .processElements(GrMethodNameIndex.KEY, name, myProject, filterScope, filter, GrMethod.class, processor) &&
      StubIndex.getInstance()
               .processElements(GrAnnotationMethodNameIndex.KEY, name, myProject, filterScope, filter, GrAnnotationMethod.class, processor);
  }

  @Override
  @Nonnull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount) {
    return getMethodsByName(name, scope);
  }

  @Nonnull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount) {
    return getFieldsByName(name, scope);
  }

  @Override
  @Nonnull
  public String[] getAllMethodNames() {
    Collection<String> keys = StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject);
    keys.addAll(StubIndex.getInstance().getAllKeys(GrAnnotationMethodNameIndex.KEY, myProject));
    return ArrayUtil.toStringArray(keys);
  }

  @Override
  public void getAllMethodNames(@Nonnull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject));
  }

  @Override
  @Nonnull
  public PsiField[] getFieldsByName(@Nonnull @NonNls String name, @Nonnull GlobalSearchScope scope) {
    final Collection<? extends PsiField> fields =
      StubIndex.getInstance().get(GrFieldNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;
    return fields.toArray(new PsiField[fields.size()]);
  }

  @Override
  @Nonnull
  public String[] getAllFieldNames() {
    Collection<String> fields = StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject);
    return ArrayUtil.toStringArray(fields);
  }

  @Override
  public void getAllFieldNames(@Nonnull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject));
  }

  @Override
  public boolean processFieldsWithName(@Nonnull String name,
                                       @Nonnull Processor<? super PsiField> processor,
                                       @Nonnull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    return StubIndex.getInstance()
                    .processElements(GrFieldNameIndex.KEY,
                                     name,
                                     myProject,
                                     new GrSourceFilterScope(scope),
                                     filter,
                                     GrField.class,
                                     processor);
  }

  @Override
  public boolean processClassesWithName(@Nonnull String name,
                                        @Nonnull Processor<? super PsiClass> processor,
                                        @Nonnull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    for (GroovyFile file : StubIndex.getElements(GrScriptClassNameIndex.KEY,
                                                 name,
                                                 myProject,
                                                 new GrSourceFilterScope(scope),
                                                 filter,
                                                 GroovyFile.class)) {
      PsiClass aClass = file.getScriptClass();
      if (aClass != null && !processor.process(aClass)) return true;
    }
    return true;
  }
}
