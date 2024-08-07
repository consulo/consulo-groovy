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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.boxPrimitiveType;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.getLeastUpperBoundNullable;

/**
 * @author Maxim.Medvedev
 */
public class GrRangeType extends GrLiteralClassType {
  private final @Nullable
  PsiType myLeft;
  private final @Nullable PsiType myRight;
  private final PsiType myIterationType;
  private final String myQualifiedName;

  public GrRangeType(LanguageLevel languageLevel,
                     GlobalSearchScope scope,
                     JavaPsiFacade facade,
                     @Nullable PsiType left,
                     @Nullable PsiType right) {
    super(languageLevel, scope, facade);
    myLeft = left;
    myRight = right;
    myIterationType = boxPrimitiveType(getLeastUpperBoundNullable(myLeft, myRight, getPsiManager()), getPsiManager(), scope);
    if (TypesUtil.unboxPrimitiveTypeWrapper(myIterationType) == PsiType.INT) {
      myQualifiedName = GroovyCommonClassNames.GROOVY_LANG_INT_RANGE;
    }
    else {
      myQualifiedName = GroovyCommonClassNames.GROOVY_LANG_OBJECT_RANGE;
    }
  }

  public GrRangeType(GlobalSearchScope scope, JavaPsiFacade facade, @Nullable PsiType left, @Nullable PsiType right) {
    this(LanguageLevel.JDK_1_5, scope, facade, left, right);
  }

  @Nonnull
  @Override
  protected String getJavaClassName() {
    return myQualifiedName;
  }

  @Nonnull
  @Override
  public String getClassName() {
    return StringUtil.getShortName(myQualifiedName);
  }

  @Nonnull
  @Override
  public PsiType[] getParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiClassType setLanguageLevel(@Nonnull LanguageLevel languageLevel) {
    return new GrRangeType(languageLevel, myScope, myFacade, myLeft, myRight);
  }

  @Override
  public String getInternalCanonicalText() {
    return "[" +
           (myLeft == null ? "null" : myLeft.getInternalCanonicalText()) +
           ".." +
           (myRight == null ? "null" : myRight.getInternalCanonicalText()) +
           "]";
  }

  @Override
  public boolean isValid() {
    return (myLeft == null || myLeft.isValid()) && (myRight == null || myRight.isValid());
  }

  @Nullable
  public PsiType getIterationType() {
    return myIterationType;
  }

  @Nullable
  public PsiType getLeft() {
    return myLeft;
  }

  @Nullable
  public PsiType getRight() {
    return myRight;
  }
}
