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
package org.jetbrains.plugins.groovy.impl.lang;

import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import consulo.util.lang.Trinity;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.Priority;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class GroovyConstructorNamedArgumentProvider extends GroovyNamedArgumentProvider {

  private static final String METACLASS = "metaClass";

  @Override
  public void getNamedArguments(@Nonnull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, NamedArgumentDescriptor> result) {
    if (!(call instanceof GrNewExpression)) return;

    if (resolve != null) {
      if (!(resolve instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)resolve;
      if (!method.isConstructor()) return;
    }

    GrNewExpression newCall = (GrNewExpression)call;

    GrArgumentList argumentList = newCall.getArgumentList();
    if (argumentList == null) return;

    GrExpression[] expressionArguments = argumentList.getExpressionArguments();
    if (expressionArguments.length > 1 || (expressionArguments.length == 1 && !(expressionArguments[0] instanceof GrReferenceExpression))) {
      return;
    }

    for (GroovyResolveResult resolveResult : newCall.multiResolveClass()) {
      PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiClass)) continue;

      PsiClass aClass = (PsiClass)element;

      if (!isClassHasConstructorWithMap(aClass)) continue;

      PsiClassType classType = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);

      processClass(call, classType, argumentName, result);
    }
  }

  public static void processClass(@Nonnull GrCall call,
                                  PsiClassType type,
                                  @Nullable String argumentName,
                                  final Map<String, NamedArgumentDescriptor> result) {
    if (argumentName == null) {
      final HashMap<String, Trinity<PsiType, PsiElement, PsiSubstitutor>> map = new HashMap<>();

      MyPsiScopeProcessor processor = new MyPsiScopeProcessor() {
        @Override
        protected void addNamedArgument(String propertyName, PsiType type, PsiElement element, PsiSubstitutor substitutor) {
          if (result.containsKey(propertyName)) return;

          Trinity<PsiType, PsiElement, PsiSubstitutor> pair = map.get(propertyName);
          if (pair != null) {
            if (element instanceof PsiMethod && pair.second instanceof PsiField) {
              // methods should override fields
            }
            else {
              return;
            }
          }

          map.put(propertyName, Trinity.create(type, element, substitutor));
        }
      };

      processor.setResolveTargetKinds(ResolverProcessorImpl.RESOLVE_KINDS_METHOD_PROPERTY);

      ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), call);

      for (Map.Entry<String, Trinity<PsiType, PsiElement, PsiSubstitutor>> entry : map.entrySet()) {
        result.put(entry.getKey(), new NamedArgumentDescriptor.TypeCondition(entry.getValue().first, entry.getValue().getSecond(), entry.getValue().getThird()).setPriority(Priority.AS_LOCAL_VARIABLE));
      }
    }
    else {
      MyPsiScopeProcessor processor = new MyPsiScopeProcessor() {
        @Override
        protected void addNamedArgument(String propertyName, PsiType type, PsiElement element, PsiSubstitutor substitutor) {
          if (result.containsKey(propertyName)) return;
          result.put(propertyName, new NamedArgumentDescriptor.TypeCondition(type, element, substitutor).setPriority(Priority.AS_LOCAL_VARIABLE));
        }
      };

      processor.setResolveTargetKinds(ResolverProcessorImpl.RESOLVE_KINDS_METHOD);
      processor.setNameHint(GroovyPropertyUtils.getSetterName(argumentName));

      ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), call);

      processor.setResolveTargetKinds(ResolverProcessorImpl.RESOLVE_KINDS_PROPERTY);
      processor.setNameHint(argumentName);

      ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), call);
    }
  }

  private static boolean isClassHasConstructorWithMap(PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) return true;

    for (PsiMethod constructor : constructors) {
      PsiParameterList parameterList = constructor.getParameterList();

      PsiParameter[] parameters = parameterList.getParameters();

      if (parameters.length == 0) return true;

      final PsiParameter first = parameters[0];
      if (InheritanceUtil.isInheritor(first.getType(), CommonClassNames.JAVA_UTIL_MAP)) return true;
      if (first instanceof GrParameter && ((GrParameter)first).getTypeGroovy() == null) return true;

      //if constructor has only optional parameters it can be used as default constructor with map args
      if (!PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;
    }
    return false;
  }

  private static abstract class MyPsiScopeProcessor implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
    private String myNameHint;
    private EnumSet<ResolveKind> myResolveTargetKinds;

    @Override
    public boolean execute(@Nonnull PsiElement element, ResolveState state) {
      if (element instanceof PsiMethod || element instanceof PsiField) {
        String propertyName;
        PsiType type;

        if (element instanceof PsiMethod) {
          if (!myResolveTargetKinds.contains(METHOD)) return true;

          PsiMethod method = (PsiMethod)element;
          if (!GroovyPropertyUtils.isSimplePropertySetter(method)) return true;

          propertyName = GroovyPropertyUtils.getPropertyNameBySetter(method);
          if (propertyName == null) return true;

          type = method.getParameterList().getParameters()[0].getType();
        }
        else {
          if (!myResolveTargetKinds.contains(PROPERTY)) return true;

          type = ((PsiField)element).getType();
          propertyName = ((PsiField)element).getName();
        }

        if (propertyName.equals(METACLASS)) return true;

        if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return true;

        PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        if (substitutor != null) {
          type = substitutor.substitute(type);
        }

        addNamedArgument(propertyName, type, element, substitutor);
      }

      return true;
    }

    protected abstract void addNamedArgument(String propertyName, PsiType type, PsiElement element, PsiSubstitutor substitutor);

    @Override
    public <T> T getHint(@Nonnull Key<T> hintKey) {
      if ((NameHint.KEY == hintKey && myNameHint != null) || ClassHint.KEY == hintKey || ElementClassHint.KEY == hintKey) {
        //noinspection unchecked
        return (T) this;
      }

      return null;
    }

    @Override
    public void handleEvent(Event event, Object associated) {

    }

    @Override
    public boolean shouldProcess(ResolveKind resolveKind) {
      return myResolveTargetKinds.contains(resolveKind);
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
      switch (kind) {
        case CLASS:
          return shouldProcess(CLASS);

        case ENUM_CONST:
        case VARIABLE:
        case FIELD:
          return shouldProcess(PROPERTY);

        case METHOD:
          return shouldProcess(METHOD);

        case PACKAGE:
          return shouldProcess(PACKAGE);

        default:
          return false;
      }
    }

    @Override
    public String getName(ResolveState state) {
      return myNameHint;
    }

    public void setNameHint(String nameHint) {
      myNameHint = nameHint;
    }

    public void setResolveTargetKinds(EnumSet<ResolveKind> resolveTargetKinds) {
      myResolveTargetKinds = resolveTargetKinds;
    }
  }
}
