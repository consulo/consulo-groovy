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
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.IntStubIndexExtension;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GrFullClassNameIndex extends IntStubIndexExtension<PsiClass>
{
  public static final StubIndexKey<Integer,PsiClass> KEY = StubIndexKey.createIndexKey("gr.class.fqn");

  private static final GrFullClassNameIndex ourInstance = new GrFullClassNameIndex();
  public static GrFullClassNameIndex getInstance() {
    return ourInstance;
  }

  @Nonnull
  public StubIndexKey<Integer, PsiClass> getKey() {
    return KEY;
  }

  public Collection<PsiClass> get(final Integer integer, final Project project, final GlobalSearchScope scope) {
    return super.get(integer, project, new GrSourceFilterScope(scope));
  }
}