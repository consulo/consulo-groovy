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
package org.jetbrains.plugins.groovy.impl.gotoclass;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoSymbolContributor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FindSymbolParameters;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.stub.StubIndex;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyGoToSymbolContributor implements GotoSymbolContributor {
  @Override
  public void processNames(@Nonnull Processor<String> processor, @Nonnull SearchScope scope, @Nullable IdFilter filter) {
    StubIndex index = StubIndex.getInstance();
    if (!index.processAllKeys(GrFieldNameIndex.KEY, processor, (ProjectAwareSearchScope)scope, filter)) return;
    if (!index.processAllKeys(GrMethodNameIndex.KEY, processor, (ProjectAwareSearchScope)scope, filter)) return;
    if (!index.processAllKeys(GrAnnotationMethodNameIndex.KEY, processor, (ProjectAwareSearchScope)scope, filter)) return;
  }

  @Override
  public void processElementsWithName(@Nonnull String name,
                                      @Nonnull Processor<NavigationItem> processor,
                                      @Nonnull FindSymbolParameters parameters) {
    StubIndex index = StubIndex.getInstance();
    Project project = parameters.getProject();
    GlobalSearchScope scope = (GlobalSearchScope)parameters.getSearchScope();
    IdFilter filter = parameters.getIdFilter();
    if (!index.processElements(GrFieldNameIndex.KEY, name, project, scope, filter, GrField.class, processor)) return;
    if (!index.processElements(GrMethodNameIndex.KEY, name, project, scope, filter, GrMethod.class, processor)) return;
    if (!index.processElements(GrAnnotationMethodNameIndex.KEY, name, project, scope, filter, GrAnnotationMethod.class, processor)) return;
  }
}
