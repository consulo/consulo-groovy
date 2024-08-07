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
package org.jetbrains.plugins.groovy.impl.griffon;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.resolve.DefaultImportContributor;
import org.jetbrains.plugins.groovy.impl.mvc.MvcFramework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class GriffonDefaultImportContributor extends DefaultImportContributor {

  private static Pair<List<String>, List<String>> getDefaultImports(@Nonnull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, new CachedValueProvider<Pair<List<String>, List<String>>>() {
      @Override
      public Result<Pair<List<String>, List<String>>> compute() {
        PsiJavaPackage aPackage = JavaPsiFacade.getInstance(module.getProject()).findPackage("META-INF");
        if (aPackage != null) {
          for (PsiDirectory directory : aPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module))) {
            PsiFile file = directory.findFile("griffon-default-imports.properties");
            if (file instanceof PropertiesFile) {
              List<String> modelImports = tokenize(((PropertiesFile)file).findPropertyByKey("models"));
              List<String> viewImports = tokenize(((PropertiesFile)file).findPropertyByKey("views"));
              return Result.create(Pair.create(modelImports, viewImports), PsiModificationTracker.MODIFICATION_COUNT);
            }
          }
        }

        return Result.create(new Pair<List<String>, List<String>>(new ArrayList<String>(), new ArrayList<String>()),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }

      private List<String> tokenize(IProperty models) {
        List<String> modelImports = new ArrayList<String>();
        if (models != null) {
          String value = models.getValue();
          if (value != null) {
            String[] split = value.split(", ");
            for (String s : split) {
              modelImports.add(StringUtil.trimEnd(s, "."));
            }
          }
        }
        return modelImports;
      }
    });
  }

  @Override
  public List<String> appendImplicitlyImportedPackages(@Nonnull GroovyFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    MvcFramework framework = MvcFramework.getInstance(module);
    if (framework instanceof GriffonFramework) {
      ArrayList<String> result = new ArrayList<String>();
      result.add("griffon.core");
      result.add("griffon.util");

      VirtualFile griffonApp = framework.findAppDirectory(file);
      if (griffonApp != null) {
        VirtualFile models = griffonApp.findChild("models");
        VirtualFile views = griffonApp.findChild("views");
        VirtualFile vFile = file.getOriginalFile().getVirtualFile();

        assert vFile != null;
        assert module != null;
        if (models != null && VfsUtilCore.isAncestor(models, vFile, true)) {
          result.addAll(getDefaultImports(module).first);
        }
        else if (views != null && VfsUtilCore.isAncestor(views, vFile, true)) {
          result.addAll(getDefaultImports(module).second);
        }
      }

      return result;
    }

    return Collections.emptyList();
  }
}
