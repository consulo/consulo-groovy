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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.resolve.ResolveState;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ven
 */
public class MethodResolverProcessor extends ResolverProcessorImpl implements GrMethodComparator.Context {
  private final PsiType myThisType;

  @Nullable
  private final PsiType[] myArgumentTypes;

  private final boolean myAllVariants;

  private Set<GroovyResolveResult> myInapplicableCandidates = null;

  private final boolean myIsConstructor;

  private boolean myStopExecuting = false;

  private final boolean myByShape;

  private final SubstitutorComputer mySubstitutorComputer;

  public MethodResolverProcessor(@Nullable String name,
                                 @Nonnull PsiElement place,
                                 boolean isConstructor,
                                 @Nullable PsiType thisType,
                                 @Nullable PsiType[] argumentTypes,
                                 @Nullable PsiType[] typeArguments) {
    this(name, place, isConstructor, thisType, argumentTypes, typeArguments, false, false);
  }

  public MethodResolverProcessor(@Nullable String name,
                                 @Nonnull PsiElement place,
                                 boolean isConstructor,
                                 @Nullable PsiType thisType,
                                 @Nullable PsiType[] argumentTypes,
                                 @Nullable PsiType[] typeArguments,
                                 boolean allVariants,
                                 final boolean byShape) {
    super(name, RESOLVE_KINDS_METHOD_PROPERTY, place, PsiType.EMPTY_ARRAY);
    myIsConstructor = isConstructor;
    myThisType = thisType;
    myArgumentTypes = argumentTypes;
    myAllVariants = allVariants;
    myByShape = byShape;

    mySubstitutorComputer = new SubstitutorComputer(myThisType, myArgumentTypes, typeArguments, myPlace,
                                                    myPlace.getParent());
  }


  @Override
  public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
    if (myStopExecuting) {
      return false;
    }
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;

      if (method.isConstructor() != myIsConstructor) {
        return true;
      }

      PsiSubstitutor substitutor = inferSubstitutor(method, state);

      PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
      final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);

      boolean isAccessible = isAccessible(method);
      boolean isStaticsOK = isStaticsOK(method, resolveContext, false);
      boolean isApplicable = PsiUtil.isApplicable(myArgumentTypes, method, substitutor, myPlace, myByShape);
      boolean isValidResult = isStaticsOK && isAccessible && isApplicable;

      GroovyResolveResultImpl candidate = new GroovyResolveResultImpl(method, resolveContext, spreadState,
                                                                      substitutor, isAccessible, isStaticsOK, false, isValidResult);

      if (!myAllVariants && isValidResult) {
        addCandidate(candidate);
      }
      else {
        addInapplicableCandidate(candidate);
      }
    }

    return true;
  }

  protected boolean addInapplicableCandidate(@Nonnull GroovyResolveResult candidate) {
    if (myInapplicableCandidates == null) {
      myInapplicableCandidates = new LinkedHashSet<>();
    }
    return myInapplicableCandidates.add(candidate);
  }

  @Nonnull
  private PsiSubstitutor inferSubstitutor(@Nonnull PsiMethod method, @Nonnull ResolveState state) {
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (substitutor == null) {
      substitutor = PsiSubstitutor.EMPTY;
    }

    return myByShape ? substitutor : mySubstitutorComputer.obtainSubstitutor(substitutor, method, state);
  }

  @Override
  @Nonnull
  public GroovyResolveResult[] getCandidates() {
    if (!myAllVariants && hasApplicableCandidates()) {
      return filterCandidates();
    }
    if (myInapplicableCandidates != null && !myInapplicableCandidates.isEmpty()) {
      Set<GroovyResolveResult> resultSet = myAllVariants ? myInapplicableCandidates :
        filterCorrectParameterCount(myInapplicableCandidates);
      return ResolveUtil.filterSameSignatureCandidates(resultSet);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private Set<GroovyResolveResult> filterCorrectParameterCount(Set<GroovyResolveResult> candidates) {
    if (myArgumentTypes == null) {
      return candidates;
    }
    Set<GroovyResolveResult> result = new HashSet<GroovyResolveResult>();
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod && ((PsiMethod)element).getParameterList().getParametersCount() ==
        myArgumentTypes.length) {
        result.add(candidate);
      }
    }
    if (!result.isEmpty()) {
      return result;
    }
    return candidates;
  }

  private GroovyResolveResult[] filterCandidates() {
    List<GroovyResolveResult> array = getCandidatesInternal();
    if (array.size() == 1) {
      return array.toArray(new GroovyResolveResult[array.size()]);
    }

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();

    Iterator<GroovyResolveResult> itr = array.iterator();

    result.add(itr.next());

    Outer:
    while (itr.hasNext()) {
      GroovyResolveResult resolveResult = itr.next();
      PsiElement currentElement = resolveResult.getElement();
      if (currentElement instanceof PsiMethod) {
        PsiMethod currentMethod = (PsiMethod)currentElement;
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext(); ) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          PsiElement other = otherResolveResult.getElement();
          if (other instanceof PsiMethod) {
            PsiMethod otherMethod = (PsiMethod)other;
            int res = compareMethods(currentMethod, resolveResult.getSubstitutor(),
                                     resolveResult.getCurrentFileResolveContext(), otherMethod,
                                     otherResolveResult.getSubstitutor(), otherResolveResult.getCurrentFileResolveContext
                ());
            if (res > 0) {
              continue Outer;
            }
            else if (res < 0) {
              iterator.remove();
            }
          }
        }
      }

      result.add(resolveResult);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  /**
   * @return 1 if second is more preferable
   * 0 if methods are equal
   * -1 if first is more preferable
   */
  private int compareMethods(@Nonnull PsiMethod method1,
                             @Nonnull PsiSubstitutor substitutor1,
                             @Nullable PsiElement resolveContext1,
                             @Nonnull PsiMethod method2,
                             @Nonnull PsiSubstitutor substitutor2,
                             @Nullable PsiElement resolveContext2) {
    if (!method1.getName().equals(method2.getName())) {
      return 0;
    }

    if (secondMethodIsPreferable(method1, substitutor1, resolveContext1, method2, substitutor2, resolveContext2)) {
      if (secondMethodIsPreferable(method2, substitutor2, resolveContext2, method1, substitutor1,
                                   resolveContext1)) {
        if (method2 instanceof GrGdkMethod && !(method1 instanceof GrGdkMethod)) {
          return -1;
        }
      }
      return 1;
    }
    if (secondMethodIsPreferable(method2, substitutor2, resolveContext2, method1, substitutor1, resolveContext1)) {
      return -1;
    }

    return 0;
  }

  //method1 has more general parameter types thn method2
  private boolean secondMethodIsPreferable(@Nonnull PsiMethod method1,
                                           @Nonnull PsiSubstitutor substitutor1,
                                           @Nullable PsiElement resolveContext1,
                                           @Nonnull PsiMethod method2,
                                           @Nonnull PsiSubstitutor substitutor2,
                                           @Nullable PsiElement resolveContext2) {
    if (!method1.getName().equals(method2.getName())) {
      return false;
    }

    final Boolean custom = GrMethodComparator.checkDominated(method1, substitutor1, method2, substitutor2, this);
    if (custom != null) {
      return custom;
    }

    PsiType[] argTypes = myArgumentTypes;
    if (method1 instanceof GrGdkMethod && method2 instanceof GrGdkMethod) {
      method1 = ((GrGdkMethod)method1).getStaticMethod();
      method2 = ((GrGdkMethod)method2).getStaticMethod();
      if (myArgumentTypes != null) {
        argTypes = PsiType.createArray(argTypes.length + 1);
        System.arraycopy(myArgumentTypes, 0, argTypes, 1, myArgumentTypes.length);
        argTypes[0] = myThisType;
      }
    }
    else if (method1 instanceof GrGdkMethod) {
      return true;
    }
    else if (method2 instanceof GrGdkMethod) {
      return false;
    }

    if (myIsConstructor && argTypes != null && argTypes.length == 1) {
      if (method1.getParameterList().getParametersCount() == 0) {
        return true;
      }
      if (method2.getParameterList().getParametersCount() == 0) {
        return false;
      }
    }

    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();
    if (argTypes == null && params1.length != params2.length) {
      return false;
    }

    if (params1.length < params2.length) {
      if (params1.length == 0) {
        return false;
      }
      final PsiType lastType = params1[params1.length - 1].getType(); //varargs applicability
      return lastType instanceof PsiArrayType;
    }

    for (int i = 0; i < params2.length; i++) {
      final PsiType ptype1 = params1[i].getType();
      final PsiType ptype2 = params2[i].getType();
      PsiType type1 = substitutor1.substitute(ptype1);
      PsiType type2 = substitutor2.substitute(ptype2);

      if (argTypes != null && argTypes.length > i) {
        PsiType argType = argTypes[i];
        if (argType != null) {
          final boolean converts1 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure
            (type1), argType, myPlace);
          final boolean converts2 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure
            (type2), argType, myPlace);
          if (converts1 != converts2) {
            return converts2;
          }

          // see groovy.lang.GroovyCallable
          if (TypesUtil.resolvesTo(type1, CommonClassNames.JAVA_UTIL_CONCURRENT_CALLABLE) && TypesUtil
            .resolvesTo(type2, CommonClassNames.JAVA_LANG_RUNNABLE)) {
            if (InheritanceUtil.isInheritor(argType, GroovyCommonClassNames.GROOVY_LANG_GROOVY_CALLABLE)) {
              return true;
            }
          }

        }
      }

      if (!typesAgree(TypeConversionUtil.erasure(ptype1), TypeConversionUtil.erasure(ptype2))) {
        return false;
      }

      if (resolveContext1 != null && resolveContext2 == null) {
        return !(TypesUtil.resolvesTo(type1, CommonClassNames.JAVA_LANG_OBJECT) && TypesUtil.resolvesTo(type2,
                                                                                                        CommonClassNames.JAVA_LANG_OBJECT));
      }

      if (resolveContext1 == null && resolveContext2 != null) {
        return true;
      }
    }

    if (!(method1 instanceof SyntheticElement) && !(method2 instanceof SyntheticElement)) {
      final PsiType returnType1 = substitutor1.substitute(method1.getReturnType());
      final PsiType returnType2 = substitutor2.substitute(method2.getReturnType());

      if (!TypesUtil.isAssignableWithoutConversions(returnType1, returnType2,
                                                    myPlace) && TypesUtil.isAssignableWithoutConversions(returnType2,
                                                                                                         returnType1,
                                                                                                         myPlace)) {
        return false;
      }
    }

    return true;
  }

  private boolean typesAgree(@Nonnull PsiType type1, @Nonnull PsiType type2) {
    if (argumentsSupplied() && type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
      type1 = ((PsiArrayType)type1).getComponentType();
    }
    return argumentsSupplied() ? //resolve, otherwise same_name_variants
      TypesUtil.isAssignableWithoutConversions(type1, type2, myPlace) : type1.equals(type2);
  }

  private boolean argumentsSupplied() {
    return myArgumentTypes != null;
  }


  @Override
  public boolean hasCandidates() {
    return hasApplicableCandidates() || myInapplicableCandidates != null && !myInapplicableCandidates.isEmpty();
  }

  public boolean hasApplicableCandidates() {
    return super.hasCandidates();
  }

  @Override
  @Nullable
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Nullable
  @Override
  public PsiType[] getTypeArguments() {
    return mySubstitutorComputer.getTypeArguments();
  }

  @Override
  public void handleEvent(@Nonnull Event event, Object associated) {
    super.handleEvent(event, associated);
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && hasApplicableCandidates()) {
      myStopExecuting = true;
    }
  }

  @Nullable
  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @Nonnull
  @Override
  public PsiElement getPlace() {
    return myPlace;
  }

}
