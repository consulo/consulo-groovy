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

import com.intellij.java.execution.impl.junit.JUnitUtil;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.action.PsiLocation;
import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.image.Image;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: Dmitry.Krasilschikov
 * Date: 15.04.2009
 */
public class TestClassNode extends ClassNode {
  private final Image myMethodIcon;

  public TestClassNode(@Nonnull final Module module,
                       @Nonnull final GrTypeDefinition controllerClass,
                       @Nullable final ViewSettings viewSettings, final Image methodIcon) {
    super(module, controllerClass, viewSettings);
    myMethodIcon = methodIcon;
  }

  @Nullable
  @Override
  protected MethodNode createNodeForMethod(final Module module, final GrMethod method) {
    if (method == null) return null;

    final boolean isTestMethod = JUnitUtil.isTestMethod(new PsiLocation<PsiMethod>(getProject(), method));

    if (isTestMethod) {
      return new TestMethodNode(module, method, getSettings(), myMethodIcon);
    }

    return new MethodNode(module, method, getSettings());
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull final PsiElement psiElement) {
    return "Test class: " + ((GrTypeDefinition)psiElement).getName();
  }
}
