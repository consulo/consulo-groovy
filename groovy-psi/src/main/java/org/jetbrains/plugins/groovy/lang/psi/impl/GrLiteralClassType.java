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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nonnull;

import java.util.function.Function;

/**
 * @author peter
 */
public abstract class GrLiteralClassType extends PsiClassType {
  protected final GlobalSearchScope myScope;
  protected final JavaPsiFacade myFacade;
  private final GroovyPsiManager myGroovyPsiManager;

  public GrLiteralClassType(@Nonnull LanguageLevel languageLevel, @Nonnull GlobalSearchScope scope, @Nonnull JavaPsiFacade facade) {
    super(languageLevel);
    myScope = scope;
    myFacade = facade;
    myGroovyPsiManager = GroovyPsiManager.getInstance(myFacade.getProject());
  }

  @Nonnull
  protected abstract String getJavaClassName();

  @Nonnull
  public ClassResolveResult resolveGenerics() {
    final PsiClass myBaseClass = resolve();
    final PsiSubstitutor substitutor;
    if (myBaseClass != null) {
      final PsiType[] typeArgs = getParameters();
      final PsiTypeParameter[] typeParams = myBaseClass.getTypeParameters();
      if (typeParams.length == typeArgs.length) {
        substitutor = PsiSubstitutor.EMPTY.putAll(myBaseClass, typeArgs);
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    else {
      substitutor = PsiSubstitutor.EMPTY;
    }

    return new ClassResolveResult() {

      public PsiClass getElement() {
        return myBaseClass;
      }

      public PsiSubstitutor getSubstitutor() {
        return substitutor;
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      public boolean isAccessible() {
        return true;
      }

      public boolean isStaticsScopeCorrect() {
        return true;
      }

      @Nullable
      public PsiElement getCurrentFileResolveScope() {
        return null;
      }

      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @Override
  @Nonnull
  public abstract String getClassName() ;

  @Nonnull
  public String getPresentableText() {
    String name = getClassName();
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    return name + "<" + StringUtil.join(params, psiType -> psiType.getPresentableText(), ", ") + ">";
  }

  @Nonnull
  public String getCanonicalText() {
    String name = getJavaClassName();
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    final Function<PsiType, String> f = psiType -> psiType.getCanonicalText();
    return name + "<" + StringUtil.join(params, f, ", ") + ">";
  }

  @Nonnull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Nonnull
  public GlobalSearchScope getScope() {
    return myScope;
  }

  @Nullable
  public PsiClass resolve() {
    return myGroovyPsiManager.findClassWithCache(getJavaClassName(), getResolveScope());
  }

  @Nonnull
  public PsiClassType rawType() {
    return myGroovyPsiManager.createTypeByFQClassName(getJavaClassName(), myScope);
  }

  public boolean equalsToText(@NonNls String text) {
    return text != null && text.equals(getJavaClassName());
  }

  @Nonnull
  public GlobalSearchScope getResolveScope() {
    return myScope;
  }

  protected static String getInternalCanonicalText(@Nullable PsiType type) {
    return type == null ? CommonClassNames.JAVA_LANG_OBJECT : type.getInternalCanonicalText();
  }

  @Nonnull
  protected PsiType getLeastUpperBound(PsiType[] psiTypes) {
    PsiType result = null;
    final PsiManager manager = getPsiManager();
    for (final PsiType other : psiTypes) {
      result = TypesUtil.getLeastUpperBoundNullable(result, other, manager);
    }
    return result == null ? PsiType.getJavaLangObject(manager, getResolveScope()) : result;
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(myFacade.getProject());
  }
}
