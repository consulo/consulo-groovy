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
package org.jetbrains.plugins.groovy.impl.dgm;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GroovyExtensionProvider {
  @NonNls
  public static final String ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE = "org.codehaus.groovy.runtime.ExtensionModule";
  private final Project myProject;

  @Inject
  public GroovyExtensionProvider(Project project) {
    myProject = project;
  }

  public static GroovyExtensionProvider getInstance(Project project) {
    return ServiceManager.getService(project, GroovyExtensionProvider.class);
  }

  public Pair<List<String>, List<String>> collectExtensions(GlobalSearchScope resolveScope) {
    PsiJavaPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("META-INF.services");
    if (aPackage == null) {
      return new Pair<List<String>, List<String>>(Collections.<String>emptyList(), Collections.<String>emptyList());
    }


    List<String> instanceClasses = new ArrayList<String>();
    List<String> staticClasses = new ArrayList<String>();
    for (PsiDirectory directory : aPackage.getDirectories(resolveScope)) {
      PsiFile file = directory.findFile("org.codehaus.groovy.runtime.ExtensionModule");
      if (file instanceof PropertiesFile) {
        IProperty inst = ((PropertiesFile)file).findPropertyByKey("extensionClasses");
        IProperty stat = ((PropertiesFile)file).findPropertyByKey("staticExtensionClasses");

        if (inst != null) collectClasses(inst, instanceClasses);
        if (stat != null) collectClasses(stat, staticClasses);
      }
    }

    return new Pair<List<String>, List<String>>(instanceClasses, staticClasses);
  }

  private static void collectClasses(IProperty pr, List<String> classes) {
    String value = pr.getUnescapedValue();
    if (value == null) return;
    value = value.trim();
    String[] qnames = value.split("\\s*,\\s*");
    ContainerUtil.addAll(classes, qnames);
  }
}
