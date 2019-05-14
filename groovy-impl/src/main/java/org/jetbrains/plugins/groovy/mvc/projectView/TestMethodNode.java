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

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import consulo.ui.image.Image;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: Dmitry.Krasilschikov
 * Date: 15.04.2009
 */
public class TestMethodNode extends MethodNode {
  private final Image myIcon;

  public TestMethodNode(@Nonnull final Module module,
                        @Nonnull final GrMethod method,
                        @Nullable final ViewSettings viewSettings, final Image icon) {
    super(module, method, viewSettings);
    myIcon = icon;
  }

  @Override
  protected GrMethod extractPsiFromValue() {
    return (GrMethod)super.extractPsiFromValue();
  }

  @Override
  protected String getTestPresentationImpl( @Nonnull final PsiElement psiElement) {
    return "Test method: " + ((GrField)psiElement).getName();
  }

  @Override
  protected void updateImpl(final PresentationData data) {
    super.updateImpl(data);

    data.setIcon(myIcon);
  }

}
