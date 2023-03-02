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
package org.jetbrains.plugins.groovy.impl.lang.resolve;

import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import javax.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
public abstract class ClosureMemberContributor extends NonCodeMembersContributor {
  @Override
  public final void processDynamicElements(@Nonnull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof GrClosureType)) return;

    final PsiElement context = state.get(ResolverProcessorImpl.RESOLVE_CONTEXT);
    if (!(context instanceof GrClosableBlock)) return;

    processMembers((GrClosableBlock)context, processor, place, state);
  }

  protected abstract void processMembers(@Nonnull GrClosableBlock closure,
                                         PsiScopeProcessor processor,
                                         PsiElement place,
                                         ResolveState state);
}
