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
package org.jetbrains.plugins.groovy.impl.findUsages;

import consulo.annotation.component.ExtensionImpl;
import consulo.component.util.Iconable;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiDirectory;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.ui.image.Image;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GrFileItemPresentationProvider implements ItemPresentationProvider<GroovyFileImpl> {
  @Nonnull
  @Override
  public Class<GroovyFileImpl> getItemClass() {
    return GroovyFileImpl.class;
  }

  @Override
  public ItemPresentation getPresentation(final GroovyFileImpl file) {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return GroovyBundle.message("groovy.file.0", file.getName());
      }

      @Override
      public String getLocationString() {
        PsiDirectory directory = file.getContainingDirectory();
        return ItemPresentationProvider.getItemPresentation(directory).getPresentableText();
      }

      @Override
      public Image getIcon() {
        return IconDescriptorUpdaters.getIcon(file, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}
