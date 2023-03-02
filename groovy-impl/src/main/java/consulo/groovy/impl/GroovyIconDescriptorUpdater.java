/*
 * Copyright 2013 Consulo.org
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
package consulo.groovy.impl;

import com.intellij.java.language.psi.PsiModifier;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 22:25/19.07.13
 */
@ExtensionImpl(order = "after java", id = "groovy")
public class GroovyIconDescriptorUpdater implements IconDescriptorUpdater {
  public static final Key<Image> ICON_KEY = Key.create("groovy-icon-key");

  @RequiredReadAction
  @Override
  public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags) {
    Image image = element.getUserData(ICON_KEY);
    if (image != null) {
      iconDescriptor.setMainIcon(image);
    }

    if (element instanceof GroovyFile) {
      GroovyFile file = (GroovyFile)element;
      final GrTypeDefinition[] typeDefinitions = file.getTypeDefinitions();
      if (typeDefinitions.length == 1) {
        IconDescriptorUpdaters.processExistingDescriptor(iconDescriptor, typeDefinitions[0], flags);
      }
      else {
        iconDescriptor.setMainIcon(GroovyScriptTypeDetector.getIcon(file));
      }
    }
    else if (element instanceof GrTypeDefinition) {
      final GrTypeDefinition psiClass = (GrTypeDefinition)element;
      if (psiClass.isEnum()) {
        iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.Enum);
      }
      else if (psiClass.isAnnotationType()) {
        iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.AnnotationType);
      }
      else if (psiClass.isInterface()) {
        iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.Interface);
      }
      else if (psiClass.isTrait()) {
        iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.Trait);
      }
      else {
        final boolean abst = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
        iconDescriptor.setMainIcon(abst ? JetgroovyIcons.Groovy.AbstractClass : JetgroovyIcons.Groovy.Class);
      }

      // if(!DumbService.getInstance(element.getProject()).isDumb()) {
    /*if (GroovyRunnerUtil.isRunnable(psiClass)) {
          iconDescriptor.addLayerIcon(AllIcons.Nodes.RunnableMark);
        }*/
      // }
    }
  }
}
