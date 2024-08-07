// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.impl.gpp;

import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.AbstractClosureParameterEnhancer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class GppClosureParameterTypeProvider extends AbstractClosureParameterEnhancer {
  @Override
  protected PsiType getClosureParameterType(@Nonnull GrClosableBlock expression, int index) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof GrNamedArgument) {
      final Pair<PsiMethod, PsiSubstitutor> pair = getOverriddenMethod((GrNamedArgument)parent);
      if (pair != null) {
        final PsiParameter[] parameters = pair.first.getParameterList().getParameters();
        if (parameters.length > index) {
          return pair.second.substitute(parameters[index].getType());
        }
        return null;
      }
    }

    return null;
  }

  @Nullable
  public static Pair<PsiMethod, PsiSubstitutor> getOverriddenMethod(GrNamedArgument namedArgument) {
    return ContainerUtil.getFirstItem(getOverriddenMethodVariants(namedArgument), null);
  }

  @Nonnull
  public static List<Pair<PsiMethod, PsiSubstitutor>> getOverriddenMethodVariants(GrNamedArgument namedArgument) {

    final GrArgumentLabel label = namedArgument.getLabel();
    if (label == null) {
      return Collections.emptyList();
    }

    final String methodName = label.getName();
    if (methodName == null) {
      return Collections.emptyList();
    }

    final PsiElement map = namedArgument.getParent();
    if (map instanceof GrListOrMap && ((GrListOrMap)map).isMap()) {
      for (PsiType expected : GroovyExpectedTypesProvider.getDefaultExpectedTypes((GrExpression)map)) {
        if (expected instanceof PsiClassType) {
          final List<Pair<PsiMethod, PsiSubstitutor>> pairs = getMethodsToOverrideImplementInInheritor((PsiClassType)expected, false);
          return ContainerUtil.findAll(pairs, pair -> methodName.equals(pair.first.getName()));
        }
      }
    }

    return Collections.emptyList();
  }

  public static PsiType[] getParameterTypes(final Pair<PsiMethod, PsiSubstitutor> pair) {
    return ContainerUtil.map2Array(pair.first.getParameterList().getParameters(),
                                   PsiType.class,
                                   psiParameter -> pair.second.substitute(psiParameter.getType()));
  }

  @Nonnull
  public static List<Pair<PsiMethod, PsiSubstitutor>> getMethodsToOverrideImplementInInheritor(PsiClassType classType,
                                                                                               boolean toImplement) {
    final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) {
      return Collections.emptyList();
    }

    List<Pair<PsiMethod, PsiSubstitutor>> over = getMethodsToOverrideImplement(psiClass, false);
    List<Pair<PsiMethod, PsiSubstitutor>> impl = getMethodsToOverrideImplement(psiClass, true);

    for (PsiMethod method : psiClass.getMethods()) {
      (method.hasModifierProperty(PsiModifier.ABSTRACT) ? impl : over).add(Pair.create(method, PsiSubstitutor.EMPTY));
    }

    for (Iterator<Pair<PsiMethod, PsiSubstitutor>> iterator = impl.iterator(); iterator.hasNext(); ) {
      Pair<PsiMethod, PsiSubstitutor> pair = iterator.next();
      if (hasTraitImplementation(pair.first)) {
        iterator.remove();
        over.add(pair);
      }
    }

    final List<Pair<PsiMethod, PsiSubstitutor>> result = toImplement ? impl : over;
    for (int i = 0, resultSize = result.size(); i < resultSize; i++) {
      Pair<PsiMethod, PsiSubstitutor> pair = result.get(i);
      result.set(i, Pair.create(pair.first, resolveResult.getSubstitutor().putAll(pair.second)));
    }
    return result;
  }

  private static ArrayList<Pair<PsiMethod, PsiSubstitutor>> getMethodsToOverrideImplement(PsiClass psiClass, final boolean toImplement) {
    final ArrayList<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<>();
    for (CandidateInfo info : OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, toImplement)) {
      result.add(Pair.create((PsiMethod)info.getElement(), info.getSubstitutor()));
    }
    return result;
  }

  private static boolean hasTraitImplementation(PsiMethod method) {
    return method.getModifierList().hasAnnotation("org.mbte.groovypp.runtime.HasDefaultImplementation");
  }
}
