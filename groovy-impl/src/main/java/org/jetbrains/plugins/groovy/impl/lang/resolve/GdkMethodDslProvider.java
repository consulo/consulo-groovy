/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.resolve;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.VolatileNotNullLazyValue;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import org.jetbrains.plugins.groovy.impl.dgm.GdkMethodHolder;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import jakarta.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
@SuppressWarnings({"MethodMayBeStatic"})
public class GdkMethodDslProvider implements GdslMembersProvider {

  public void category(String className, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, false);
  }

  public void category(String className, final boolean isStatic, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, isStatic);
  }

  private static void processCategoryMethods(final String className, final GdslMembersHolderConsumer consumer, final boolean isStatic) {
    final GlobalSearchScope scope = consumer.getResolveScope();
    final PsiClass categoryClass = GroovyPsiManager.getInstance(consumer.getProject()).findClassWithCache(className, scope);
    if (categoryClass == null) {
      return;
    }

    final VolatileNotNullLazyValue<GdkMethodHolder> methodsMap = new VolatileNotNullLazyValue<GdkMethodHolder>() {
      @Nonnull
      @Override
      protected GdkMethodHolder compute() {
        return GdkMethodHolder.getHolderForClass(categoryClass, isStatic, scope);
      }
    };

    consumer.addMemberHolder(new CustomMembersHolder() {

      @Override
      public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
        return methodsMap.getValue().processMethods(processor, state, descriptor.getPsiType(), descriptor.getProject());
      }
    });
  }
}
