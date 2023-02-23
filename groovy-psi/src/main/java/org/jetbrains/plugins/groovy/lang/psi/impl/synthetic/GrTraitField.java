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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.psi.PsiMirrorElement;
import consulo.logging.Logger;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import javax.annotation.Nonnull;

/**
 * Created by Max Medvedev on 19/05/14
 */
public class GrTraitField extends GrLightField implements PsiMirrorElement {
  private static final Logger LOG = Logger.getInstance(GrTraitField.class);

  private final PsiField myField;

  public GrTraitField(@Nonnull PsiField field, GrTypeDefinition clazz, PsiSubstitutor substitutor) {
    super(clazz, getNewNameForField(field), substitutor.substitute(field.getType()), field);
    GrLightModifierList modifierList = getModifierList();
    for (String modifier : PsiModifier.MODIFIERS) {
      if (field.hasModifierProperty(modifier)) {
        modifierList.addModifier(modifier);
      }
    }
    myField = field;
  }

  @Nonnull
  private static String getNewNameForField(@Nonnull PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    LOG.assertTrue(containingClass != null);
    return GrTraitUtil.getTraitFieldPrefix(containingClass) + field.getName();
  }

  @Nonnull
  @Override
  public PsiField getPrototype() {
    return myField;
  }
}
