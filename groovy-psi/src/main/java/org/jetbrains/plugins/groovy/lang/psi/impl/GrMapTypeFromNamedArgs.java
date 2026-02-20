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

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiType;
import consulo.application.util.RecursionManager;
import consulo.application.util.VolatileNotNullLazyValue;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * Created by Max Medvedev on 07/04/14
 */
public class GrMapTypeFromNamedArgs extends GrMapType {

  private final LinkedHashMap<String, GrExpression> myStringEntries;
  private final List<Couple<GrExpression>> myOtherEntries;

  private final VolatileNotNullLazyValue<List<Couple<PsiType>>> myTypesOfOtherEntries = new
    VolatileNotNullLazyValue<List<Couple<PsiType>>>() {
      @Nonnull
      @Override
      protected List<Couple<PsiType>> compute() {
        return ContainerUtil.map(myOtherEntries,
                                 pair -> Couple.of(inferTypePreventingRecursion(pair.first), inferTypePreventingRecursion(pair
                                                                                                                            .second)));
      }
    };

  private final VolatileNotNullLazyValue<LinkedHashMap<String, PsiType>> myTypesOfStringEntries =
    new VolatileNotNullLazyValue<LinkedHashMap<String, PsiType>>() {
      @Nonnull
      @Override
      protected LinkedHashMap<String, PsiType> compute() {
        LinkedHashMap<String, PsiType> result = new LinkedHashMap<>();
        for (Map.Entry<String, GrExpression> entry : myStringEntries.entrySet()) {
          result.put(entry.getKey(), inferTypePreventingRecursion(entry.getValue()));
        }
        return result;
      }

    };

  public GrMapTypeFromNamedArgs(@Nonnull PsiElement context, @Nonnull GrNamedArgument[] namedArgs) {
    this(JavaPsiFacade.getInstance(context.getProject()), context.getResolveScope(), namedArgs);
  }

  public GrMapTypeFromNamedArgs(@Nonnull JavaPsiFacade facade,
                                @Nonnull GlobalSearchScope scope,
                                @Nonnull GrNamedArgument[] namedArgs) {
    super(facade, scope);

    myStringEntries = new LinkedHashMap<>();
    myOtherEntries = ContainerUtil.newArrayList();
    for (GrNamedArgument namedArg : namedArgs) {
      GrArgumentLabel label = namedArg.getLabel();
      GrExpression expression = namedArg.getExpression();
      if (label == null || expression == null) {
        continue;
      }

      String name = label.getName();
      if (name != null) {
        myStringEntries.put(name, expression);
      }
      else if (label.getExpression() != null) {
        myOtherEntries.add(Couple.of(label.getExpression(), expression));
      }
    }
  }

  @Nullable
  @Override
  public PsiType getTypeByStringKey(String key) {
    GrExpression expression = myStringEntries.get(key);
    return expression != null ? inferTypePreventingRecursion(expression) : null;
  }

  @Nonnull
  @Override
  public Set<String> getStringKeys() {
    return myStringEntries.keySet();
  }

  @Override
  public boolean isEmpty() {
    return myStringEntries.isEmpty() && myOtherEntries.isEmpty();
  }

  @Nonnull
  @Override
  protected PsiType[] getAllKeyTypes() {
    Set<PsiType> result = new HashSet<>();
    if (!myStringEntries.isEmpty()) {
      result.add(GroovyPsiManager.getInstance(myFacade.getProject()).createTypeByFQClassName(CommonClassNames
                                                                                               .JAVA_LANG_STRING, getResolveScope()));
    }
    for (Couple<GrExpression> entry : myOtherEntries) {
      result.add(inferTypePreventingRecursion(entry.first));
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @Nonnull
  @Override
  protected PsiType[] getAllValueTypes() {
    Set<PsiType> result = new HashSet<>();
    for (GrExpression expression : myStringEntries.values()) {
      result.add(inferTypePreventingRecursion(expression));
    }
    for (Couple<GrExpression> entry : myOtherEntries) {
      result.add(inferTypePreventingRecursion(entry.second));
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @Nullable
  private static PsiType inferTypePreventingRecursion(final GrExpression expression) {
    return RecursionManager.doPreventingRecursion(expression, false, new Computable<PsiType>() {
      @Override
      public PsiType compute() {
        return expression.getType();
      }
    });
  }

  @Nonnull
  @Override
  protected List<Couple<PsiType>> getOtherEntries() {
    return myTypesOfOtherEntries.getValue();
  }

  @Nonnull
  @Override
  protected LinkedHashMap<String, PsiType> getStringEntries() {
    return myTypesOfStringEntries.getValue();
  }

  @Override
  public boolean isValid() {
    for (GrExpression expression : myStringEntries.values()) {
      if (!expression.isValid()) {
        return false;
      }
    }

    for (Couple<GrExpression> entry : myOtherEntries) {
      if (!entry.first.isValid()) {
        return false;
      }
      if (!entry.second.isValid()) {
        return false;
      }
    }

    return true;
  }
}
