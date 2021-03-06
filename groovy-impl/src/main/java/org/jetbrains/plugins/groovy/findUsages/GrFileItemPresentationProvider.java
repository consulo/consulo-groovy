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
package org.jetbrains.plugins.groovy.findUsages;

import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiDirectory;
import consulo.ide.IconDescriptorUpdaters;
import consulo.ui.image.Image;

public class GrFileItemPresentationProvider implements ItemPresentationProvider<GroovyFile> {
  @Override
  public ItemPresentation getPresentation(final GroovyFile file) {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return GroovyBundle.message("groovy.file.0", file.getName());
      }

      @Override
      public String getLocationString() {
        PsiDirectory directory = file.getContainingDirectory();
        return ItemPresentationProviders.getItemPresentation(directory).getPresentableText();
      }

      @Override
      public Image getIcon() {
        return IconDescriptorUpdaters.getIcon(file, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}
