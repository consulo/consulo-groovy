/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.extensions;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GroovyUnresolvedHighlightFilter {

  public static final ExtensionPointName<GroovyUnresolvedHighlightFilter> EP_NAME =
    ExtensionPointName.create(GroovyUnresolvedHighlightFilter.class);

  public abstract boolean isReject(@Nonnull GrReferenceExpression expression);

  public static boolean shouldHighlight(@Nonnull GrReferenceExpression expression) {
    for (GroovyUnresolvedHighlightFilter filter : EP_NAME.getExtensionList()) {
      if (filter.isReject(expression)) return false;
    }

    return true;
  }
}
