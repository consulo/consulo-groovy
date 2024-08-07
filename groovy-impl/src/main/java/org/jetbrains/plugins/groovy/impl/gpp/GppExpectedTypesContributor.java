package org.jetbrains.plugins.groovy.impl.gpp;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesContributor;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
@ExtensionImpl
public class GppExpectedTypesContributor extends GroovyExpectedTypesContributor {
  @Override
  public List<TypeConstraint> calculateTypeConstraints(@Nonnull GrExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof GrListOrMap) {
      final GrListOrMap list = (GrListOrMap)parent;
      if (!list.isMap()) {
        final PsiType listType = list.getType();
        if (!(listType instanceof GrTupleType)) {
          return Collections.emptyList();
        }

        return addExpectedConstructorParameters(list, list.getInitializers(), expression);
      }
    }
    if (parent instanceof GrNamedArgument) {
      final PsiElement map = parent.getParent();
      if (map instanceof GrListOrMap && "super".equals(((GrNamedArgument)parent).getLabelName())) {
        //todo expected property types
        return addExpectedConstructorParameters((GrListOrMap)map, new GrExpression[]{expression}, expression);
      }
    }
    return Collections.emptyList();
  }

  private static List<TypeConstraint> addExpectedConstructorParameters(GrListOrMap list,
                                                                       GrExpression[] args,
                                                                       GrExpression arg) {
    PsiType[] argTypes = ContainerUtil.map2Array(args, PsiType.class, grExpression -> grExpression.getType());

    final ArrayList<TypeConstraint> result = new ArrayList<TypeConstraint>();
    for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(list)) {
      if (type instanceof PsiClassType) {
        for (GroovyResolveResult resolveResult : PsiUtil.getConstructorCandidates((PsiClassType)type, argTypes, list)) {
          final PsiElement method = resolveResult.getElement();
          if (method instanceof PsiMethod && ((PsiMethod)method).isConstructor()) {
            final Map<GrExpression, Pair<PsiParameter, PsiType>> map = GrClosureSignatureUtil
              .mapArgumentsToParameters(resolveResult, list, false, true, GrNamedArgument.EMPTY_ARRAY, args, GrClosableBlock.EMPTY_ARRAY);
            if (map != null) {
              final Pair<PsiParameter, PsiType> pair = map.get(arg);
              if (pair != null) {
                result.add(SubtypeConstraint.create(pair.second));
              }
            }
          }
        }
      }
    }
    return result;
  }
}
