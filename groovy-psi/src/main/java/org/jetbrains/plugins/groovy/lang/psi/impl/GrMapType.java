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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import consulo.application.util.VolatileNotNullLazyValue;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author peter
 */
public abstract class GrMapType extends GrLiteralClassType {
  private final String myJavaClassName;

  private final VolatileNotNullLazyValue<PsiType[]> myParameters = new VolatileNotNullLazyValue<PsiType[]>() {
    @Nonnull
    @Override
    protected PsiType[] compute() {
      final PsiType[] keyTypes = getAllKeyTypes();
      final PsiType[] valueTypes = getAllValueTypes();
      if (keyTypes.length == 0 && valueTypes.length == 0) {
        return EMPTY_ARRAY;
      }

      return new PsiType[]{
        getLeastUpperBound(keyTypes),
        getLeastUpperBound(valueTypes)
      };
    }
  };

  protected GrMapType(JavaPsiFacade facade, GlobalSearchScope scope) {
    this(facade, scope, LanguageLevel.JDK_1_5);
  }

  protected GrMapType(JavaPsiFacade facade, GlobalSearchScope scope, LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);

    myJavaClassName = facade.findClass(GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP,
                                       scope) != null ? GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP : CommonClassNames.JAVA_UTIL_MAP;
  }

  @Nonnull
  @Override
  protected String getJavaClassName() {
    return myJavaClassName;
  }

  @Override
  @Nonnull
  public String getClassName() {
    return StringUtil.getShortName(myJavaClassName);
  }

  @Nullable
  public abstract PsiType getTypeByStringKey(String key);

  @Nonnull
  public abstract Set<String> getStringKeys();

  public abstract boolean isEmpty();

  @Nonnull
  protected abstract PsiType[] getAllKeyTypes();

  @Nonnull
  protected abstract PsiType[] getAllValueTypes();

  @Nonnull
  protected abstract List<Couple<PsiType>> getOtherEntries();

  @Nonnull
  protected abstract LinkedHashMap<String, PsiType> getStringEntries();

  @Override
  @Nonnull
  public PsiType[] getParameters() {
    return myParameters.getValue();
  }

  @Override
  @Nonnull
  public String getInternalCanonicalText() {
    Set<String> stringKeys = getStringKeys();
    List<Couple<PsiType>> otherEntries = getOtherEntries();

    if (stringKeys.isEmpty()) {
      if (otherEntries.isEmpty()) {
        return "[:]";
      }
      String name = getJavaClassName();
      final PsiType[] params = getParameters();
      return name + "<" + getInternalText(params[0]) + ", " + getInternalText(params[1]) + ">";
    }

    List<String> components = new ArrayList<String>();
    for (String s : stringKeys) {
      components.add("'" + s + "':" + getInternalCanonicalText(getTypeByStringKey(s)));
    }
    for (Couple<PsiType> entry : otherEntries) {
      components.add(getInternalCanonicalText(entry.first) + ":" + getInternalCanonicalText(entry.second));
    }
    boolean tooMany = components.size() > 2;
    final List<String> theFirst = components.subList(0, Math.min(2, components.size()));
    return "[" + StringUtil.join(theFirst, ", ") + (tooMany ? ",..." : "") + "]";
  }

  @Nonnull
  private static String getInternalText(@Nullable PsiType param) {
    return param == null ? "null" : param.getInternalCanonicalText();
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrMapType) {
      GrMapType other = (GrMapType)obj;
      return getStringEntries().equals(other.getStringEntries()) && getOtherEntries().equals(other
                                                                                               .getOtherEntries());
    }
    return super.equals(obj);
  }

  @Override
  public boolean isAssignableFrom(@Nonnull PsiType type) {
    return type instanceof GrMapType || super.isAssignableFrom(type);
  }

  public static GrMapType merge(GrMapType l, GrMapType r) {
    final GlobalSearchScope scope = l.getScope().intersectWith(r.getResolveScope());

		final LinkedHashMap<String, PsiType> strings = new LinkedHashMap<>();
    strings.putAll(l.getStringEntries());
    strings.putAll(r.getStringEntries());

    List<Couple<PsiType>> other = new ArrayList<Couple<PsiType>>();
    other.addAll(l.getOtherEntries());
    other.addAll(r.getOtherEntries());

    return create(l.myFacade, scope, strings, other);
  }

  public static GrMapType create(JavaPsiFacade facade,
                                 GlobalSearchScope scope,
                                 LinkedHashMap<String, PsiType> stringEntries,
                                 List<Couple<PsiType>> otherEntries) {
    return new GrMapTypeImpl(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }

  public static GrMapType create(GlobalSearchScope scope) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(scope.getProject());
    List<Couple<PsiType>> otherEntries = Collections.emptyList();
		LinkedHashMap<String, PsiType> stringEntries = new LinkedHashMap<>();
    return new GrMapTypeImpl(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }

  @Nonnull
  @Override
  public PsiClassType setLanguageLevel(@Nonnull LanguageLevel languageLevel) {
    return new GrMapTypeImpl(myFacade, getResolveScope(), getStringEntries(), getOtherEntries(), languageLevel);
  }

  public static GrMapType createFromNamedArgs(PsiElement context, GrNamedArgument[] args) {
    return new GrMapTypeFromNamedArgs(context, args);
  }

  @Override
  public String toString() {
    return "map type";
  }
}
