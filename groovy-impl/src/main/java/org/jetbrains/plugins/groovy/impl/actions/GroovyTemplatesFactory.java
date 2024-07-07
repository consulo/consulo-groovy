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

package org.jetbrains.plugins.groovy.impl.actions;

import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.fileTemplate.*;
import consulo.ide.impl.idea.codeInsight.actions.ReformatCodeProcessor;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.JetgroovyIcons;

import jakarta.annotation.Nonnull;
import java.util.Properties;

@ExtensionImpl
public class GroovyTemplatesFactory implements FileTemplateGroupDescriptorFactory {
  public static final String[] TEMPLATES = {
    GroovyTemplates.GROOVY_CLASS,
    GroovyTemplates.GROOVY_SCRIPT,
    GroovyTemplates.GANT_SCRIPT
  };

  static final String NAME_TEMPLATE_PROPERTY = "NAME";
  static final String LOW_CASE_NAME_TEMPLATE_PROPERTY = "lowCaseName";

  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group =
      new FileTemplateGroupDescriptor(GroovyBundle.message("file.template.group.title.groovy"), JetgroovyIcons.Groovy.Groovy_16x16);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (String template : TEMPLATES) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }

    return group;
  }


  public static PsiFile createFromTemplate(@Nonnull final PsiDirectory directory,
                                           @Nonnull final String name,
                                           @Nonnull String fileName,
                                           @Nonnull String templateName,
                                           boolean allowReformatting,
                                           String... parameters) throws IncorrectOperationException {
    FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(directory.getProject());
    final FileTemplate template = fileTemplateManager.getInternalTemplate(templateName);

    Project project = directory.getProject();

    Properties properties = new Properties(fileTemplateManager.getDefaultProperties());
    JavaTemplateUtil.setPackageNameAttribute(properties, directory);
    properties.setProperty(NAME_TEMPLATE_PROPERTY, name);
    properties.setProperty(LOW_CASE_NAME_TEMPLATE_PROPERTY, name.substring(0, 1).toLowerCase() + name.substring(1));
    for (int i = 0; i < parameters.length; i += 2) {
      properties.setProperty(parameters[i], parameters[i + 1]);
    }
    String text;
    try {
      text = template.getText(properties);
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to load template for " + fileTemplateManager.internalTemplateToSubject(templateName), e);
    }

    return WriteAction.compute(() -> {
      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      PsiFile file = factory.createFileFromText(fileName, GroovyFileType.GROOVY_FILE_TYPE, text);

      file = (PsiFile)directory.add(file);

      if (file != null && allowReformatting && template.isReformatCode()) {
        new ReformatCodeProcessor(project, file, null, false).run();
      }

      return file;
    });
  }
}
