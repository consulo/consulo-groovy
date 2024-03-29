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
package org.jetbrains.plugins.groovy.lang.resolve;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class ClosureMissingMethodContributor {

  public static final ExtensionPointName<ClosureMissingMethodContributor> EP_NAME =
    ExtensionPointName.create(ClosureMissingMethodContributor.class);

  public static boolean processMethodsFromClosures(GrReferenceExpressionImpl ref, PsiScopeProcessor processor) {
    for (PsiElement e = ref.getContext(); e != null; e = e.getContext()) {
      if (e instanceof GrClosableBlock) {
        ResolveState state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, e);
        for (ClosureMissingMethodContributor contributor : EP_NAME.getExtensionList()) {
          if (!contributor.processMembers((GrClosableBlock)e, processor, ref, state)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public abstract boolean processMembers(GrClosableBlock closure,
                                         PsiScopeProcessor processor,
                                         GrReferenceExpression refExpr,
                                         ResolveState state);

}
