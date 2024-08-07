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
package org.jetbrains.plugins.groovy.impl.lang.psi.typeEnhancers;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GrContainerTypeConverter extends GrTypeConverter {
  @Override
  public boolean isAllowedInMethodCall() {
    return false;
  }

  @Override
  public Boolean isConvertible(@Nonnull PsiType lType, @Nonnull PsiType rType, @Nonnull GroovyPsiElement context) {
    if (!isCollectionOrArray(lType) || !isCollectionOrArray(rType)) return null;

    final PsiType lComponentType = extractComponentType(lType);
    final PsiType rComponentType = extractComponentType(rType);

    if (lComponentType == null || rComponentType == null) return Boolean.TRUE;
    if (TypesUtil.isAssignableByMethodCallConversion(lComponentType, rComponentType, context)) return Boolean.TRUE;
    return null;
  }

  @Nullable
  private static PsiType extractComponentType(PsiType type) {
    if (type instanceof PsiArrayType) return ((PsiArrayType)type).getComponentType();
    return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_COLLECTION, 0, false);
  }

  private static boolean isCollectionOrArray(PsiType type) {
    return type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION);
  }
}
