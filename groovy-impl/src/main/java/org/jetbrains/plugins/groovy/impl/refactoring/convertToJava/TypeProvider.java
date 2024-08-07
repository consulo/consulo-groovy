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
package org.jetbrains.plugins.groovy.impl.refactoring.convertToJava;

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * @author Medvedev Max
 */
public class TypeProvider {
  private final Map<GrMethod, PsiType[]> inferredTypes = new HashMap<GrMethod, PsiType[]>();

  public TypeProvider() {
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nonnull
  public PsiType getReturnType(@Nonnull PsiMethod method) {
    if (method instanceof GrMethod) {
      GrTypeElement typeElement = ((GrMethod)method).getReturnTypeElementGroovy();
      if (typeElement != null) return typeElement.getType();
    }
    final PsiType smartReturnType = PsiUtil.getSmartReturnType(method);
    if (smartReturnType != null) return smartReturnType;

    //todo make smarter. search for usages and infer type from them
    return TypesUtil.getJavaLangObject(method);
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nonnull
  public PsiType getVarType(@Nonnull PsiVariable variable) {
    if (variable instanceof PsiParameter) return getParameterType((PsiParameter)variable);
    return getVariableTypeInner(variable);
  }

  @Nonnull
  private static PsiType getVariableTypeInner(@Nonnull PsiVariable variable) {
    PsiType type = null;
    if (variable instanceof GrVariable) {
      type = ((GrVariable)variable).getDeclaredType();
      if (type == null) {
        type = ((GrVariable)variable).getTypeGroovy();
      }
    }
    if (type == null) {
      type = variable.getType();
    }
    return type;
  }

  @Nonnull
  public PsiType getParameterType(@Nonnull PsiParameter parameter) {
    if (!(parameter instanceof GrParameter)) {
      PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof GrAccessorMethod) {
        return getVarType(((GrAccessorMethod)scope).getProperty());
      }
      return parameter.getType();
    }

    PsiElement parent = parameter.getParent();
    if (!(parent instanceof GrParameterList)) {
      return getVariableTypeInner(parameter);
    }

    PsiElement pparent = parent.getParent();
    if (!(pparent instanceof GrMethod)) return parameter.getType();

    PsiType[] types = inferMethodParameters((GrMethod)pparent);
    return types[((GrParameterList)parent).getParameterNumber((GrParameter)parameter)];
  }

  @Nonnull
  private PsiType[] inferMethodParameters(@Nonnull GrMethod method) {
    PsiType[] psiTypes = inferredTypes.get(method);
    if (psiTypes != null) return psiTypes;

    final GrParameter[] parameters = method.getParameters();

    final IntList paramInds = IntLists.newArrayList(parameters.length);
    final PsiType[] types = new PsiType[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getTypeElementGroovy() == null) {
        paramInds.add(i);
      } else {
        types[i] = parameters[i].getType();
      }
    }

    if (paramInds.size() > 0) {
      final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, PsiSubstitutor.EMPTY);
      MethodReferencesSearch.search(method, true).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference psiReference) {
          final PsiElement element = psiReference.getElement();
          final PsiManager manager = element.getManager();
          final GlobalSearchScope resolveScope = element.getResolveScope();

          if (element instanceof GrReferenceExpression) {
            final GrCall call = (GrCall)element.getParent();
            final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);

            if (argInfos == null) return true;
            paramInds.forEach(new IntConsumer() {
              @Override
              public void accept(int i) {
                PsiType type = GrClosureSignatureUtil.getTypeByArg(argInfos[i], manager, resolveScope);
                types[i] = TypesUtil.getLeastUpperBoundNullable(type, types[i], manager);
              }
            });
          }
          return true;
        }
      });
    }
    paramInds.forEach(new IntConsumer() {
      @Override
      public void accept(int i) {
        if (types[i] == null || types[i] == PsiType.NULL) {
          types[i] = parameters[i].getType();
        }
      }
    });
    inferredTypes.put(method, types);
    return types;
  }
}
