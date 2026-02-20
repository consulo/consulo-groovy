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

import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 15.04.2009
 */
public class TestMethodNode extends MethodNode {
  private final Image myIcon;

  public TestMethodNode(@Nonnull Module module,
                        @Nonnull GrMethod method,
                        @Nullable ViewSettings viewSettings, Image icon) {
    super(module, method, viewSettings);
    myIcon = icon;
  }

  @Override
  protected GrMethod extractPsiFromValue() {
    return (GrMethod)super.extractPsiFromValue();
  }

  @Override
  protected String getTestPresentationImpl( @Nonnull PsiElement psiElement) {
    return "Test method: " + ((GrField)psiElement).getName();
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);

    data.setIcon(myIcon);
  }

}
