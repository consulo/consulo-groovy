/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.RecursionManager;
import consulo.application.util.function.Computable;
import consulo.component.extension.ExtensionPointName;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class AstTransformContributor {
  public static final ExtensionPointName<AstTransformContributor> EP_NAME = ExtensionPointName.create(AstTransformContributor.class);

  public void collectMethods(@Nonnull final GrTypeDefinition clazz, Collection<PsiMethod> collector) {

  }

  public void collectFields(@Nonnull final GrTypeDefinition clazz, Collection<GrField> collector) {

  }

  public void collectClasses(@Nonnull final GrTypeDefinition clazz, Collection<PsiClass> collector) {

  }

  @Nonnull
  public static Collection<PsiMethod> runContributorsForMethods(@Nonnull final GrTypeDefinition clazz) {
    Collection<PsiMethod> result = RecursionManager.doPreventingRecursion(clazz, true, () -> {
      Collection<PsiMethod> collector = new ArrayList<PsiMethod>();
      for (final AstTransformContributor contributor : EP_NAME.getExtensionList()) {
        contributor.collectMethods(clazz, collector);
      }
      return collector;
    });
    return result == null ? Collections.<PsiMethod>emptyList() : result;
  }

  @Nonnull
  public static List<GrField> runContributorsForFields(@Nonnull final GrTypeDefinition clazz) {
    List<GrField> fields = RecursionManager.doPreventingRecursion(clazz, true, () -> {
      List<GrField> collector = new ArrayList<GrField>();
      for (final AstTransformContributor contributor : EP_NAME.getExtensionList()) {
        contributor.collectFields(clazz, collector);
      }
      return collector;
    });
    return fields != null ? fields : Collections.<GrField>emptyList();
  }

  @Nonnull
  public static List<PsiClass> runContributorsForClasses(@Nonnull final GrTypeDefinition clazz) {
    List<PsiClass> fields = RecursionManager.doPreventingRecursion(clazz, true, new Computable<List<PsiClass>>() {
      @Override
      public List<PsiClass> compute() {
        List<PsiClass> collector = ContainerUtil.newArrayList();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectClasses(clazz, collector);
        }
        return collector;
      }
    });
    return fields != null ? fields : Collections.<PsiClass>emptyList();
  }
}
