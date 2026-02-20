/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.resolve.ast;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import jakarta.annotation.Nonnull;
import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GrInheritConstructorContributor extends AstTransformContributor {

  @Override
  public void collectMethods(@Nonnull GrTypeDefinition psiClass, @Nonnull Collection<PsiMethod> collector) {
    if (psiClass.isAnonymous() || psiClass.isInterface() || psiClass.isEnum()) {
      return;
    }

    if (!hasInheritConstructorsAnnotation(psiClass)) return;

    PsiClass superClass = psiClass.getSuperClass();
    if (superClass == null) return;

    PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, PsiSubstitutor.EMPTY);
    for (PsiMethod constructor : superClass.getConstructors()) {
      if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) continue;

      GrLightMethodBuilder inheritedConstructor = new GrLightMethodBuilder(psiClass.getManager(), psiClass.getName());
      inheritedConstructor.setContainingClass(psiClass);
      inheritedConstructor.setConstructor(true);
      inheritedConstructor.setNavigationElement(psiClass);
      inheritedConstructor.addModifier(VisibilityUtil.getVisibilityModifier(constructor.getModifierList()));
      inheritedConstructor.setOriginInfo("created by @InheritConstructors");

      for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
        String name = StringUtil.notNullize(parameter.getName());
        PsiType type = superClassSubstitutor.substitute(parameter.getType());
        inheritedConstructor.addParameter(name, type, false);
      }
      if (psiClass.findCodeMethodsBySignature(inheritedConstructor, false).length == 0) {
        collector.add(inheritedConstructor);
      }
    }
  }

  public static boolean hasInheritConstructorsAnnotation(PsiClass psiClass) {
    PsiModifierList modifierList = psiClass.getModifierList();
    return modifierList != null && modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_INHERIT_CONSTRUCTORS) != null;
  }
}
