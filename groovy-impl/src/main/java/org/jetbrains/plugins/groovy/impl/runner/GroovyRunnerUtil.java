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
package org.jetbrains.plugins.groovy.impl.runner;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.execution.configuration.RunConfigurationModule;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public class GroovyRunnerUtil {
  @Nullable
  public static PsiClass getRunningClass(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;

    for (PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
         clazz != null;
         clazz = PsiTreeUtil.getParentOfType(clazz, PsiClass.class)) {
      if (canBeRunByGroovy(clazz)) return clazz;
    }

    if (((GroovyFile)file).isScript()) return ((GroovyFile)file).getScriptClass();

    PsiClass[] classes = ((GroovyFile)file).getClasses();
    if (classes.length > 0) {
      return classes[0];
    }

    return null;
  }

  public static boolean isRunnable(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;
    PsiClass runnable =
      JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CommonClassNames.JAVA_LANG_RUNNABLE, psiClass.getResolveScope());
    if (runnable == null) return false;

    return psiClass instanceof GrTypeDefinition &&
           !(psiClass instanceof PsiAnonymousClass) &&
           !psiClass.isInterface() &&
           psiClass.isInheritor(runnable, true);
  }

  public static boolean canBeRunByGroovy(PsiClass psiClass) {
    return psiClass instanceof GroovyScriptClass ||
           isRunnable(psiClass) ||
           psiClass instanceof GrTypeDefinition && PsiMethodUtil.hasMainMethod(psiClass);
  }

  public static String getConfigurationName(PsiClass aClass, RunConfigurationModule module) {
    String qualifiedName = aClass.getQualifiedName();
    Project project = module.getProject();
    if (qualifiedName == null) {
      return module.getModuleName();
    }

    PsiClass psiClass =
      JavaPsiFacade.getInstance(project).findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.projectScope(project));
    if (psiClass != null) {
      return psiClass.getName();
    }
    else {
      int lastDot = qualifiedName.lastIndexOf('.');
      if (lastDot == -1 || lastDot == qualifiedName.length() - 1) {
        return qualifiedName;
      }
      return qualifiedName.substring(lastDot + 1, qualifiedName.length());
    }
  }
}
