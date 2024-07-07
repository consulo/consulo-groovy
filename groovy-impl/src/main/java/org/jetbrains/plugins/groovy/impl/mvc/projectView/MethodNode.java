/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.module.Module;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * @author Dmitry Krasilschikov
 */
public class MethodNode extends AbstractMvcPsiNodeDescriptor {
  public MethodNode(@Nonnull final Module module,
                   @Nonnull final GrMethod method,
                   @Nullable final ViewSettings viewSettings) {
    super(module, viewSettings, method, METHOD);
  }

  @Override
  protected String getTestPresentationImpl( @Nonnull final PsiElement psiElement) {
    return "GrMethod: " + ((GrMethod)psiElement).getName();
  }

  protected Collection<AbstractTreeNode> getChildrenImpl() {
    return null;
  }
}