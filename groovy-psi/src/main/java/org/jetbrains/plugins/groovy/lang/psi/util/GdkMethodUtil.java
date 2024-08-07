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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.MixinMemberContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrDelegatingScopeProcessorWithHints;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Max Medvedev
 */
public class GdkMethodUtil {

  private static final Logger LOG = Logger.getInstance(GdkMethodUtil.class);

  public static final Set<String> COLLECTION_METHOD_NAMES = Set.of("each",
                                                                   "eachWithIndex",
                                                                   "any",
                                                                   "every",
                                                                   "reverseEach",
                                                                   "collect",
                                                                   "collectAll",
                                                                   "find",
                                                                   "findAll",
                                                                   "retainAll",
                                                                   "removeAll",
                                                                   "split",
                                                                   "groupBy",
                                                                   "groupEntriesBy",
                                                                   "findLastIndexOf",
                                                                   "findIndexValues",
                                                                   "findIndexOf");
  @NonNls
  private static final String WITH = "with";
  @NonNls
  private static final String IDENTITY = "identity";

  @NonNls
  public static final String USE = "use";
  @NonNls
  public static final String EACH_WITH_INDEX = "eachWithIndex";
  @NonNls
  public static final String INJECT = "inject";
  @NonNls
  public static final String EACH_PERMUTATION = "eachPermutation";
  @NonNls
  public static final String WITH_DEFAULT = "withDefault";
  @NonNls
  public static final String SORT = "sort";
  @NonNls
  public static final String WITH_STREAM = "withStream";
  @NonNls
  public static final String WITH_STREAMS = "withStreams";
  @NonNls
  public static final String WITH_OBJECT_STREAMS = "withObjectStreams";

  private GdkMethodUtil() {
  }

  public static boolean categoryIteration(GrClosableBlock place,
                                          final PsiScopeProcessor processor,
                                          ResolveState state) {
    final ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) {
      return true;
    }

    final GrMethodCall call = checkMethodCall(place, USE);
    if (call == null) {
      return true;
    }

    final GrClosableBlock[] closures = call.getClosureArguments();
    GrExpression[] args = call.getExpressionArguments();
    if (!(placeEqualsSingleClosureArg(place, closures) || placeEqualsLastArg(place, args))) {
      return true;
    }

    if (!(call.resolveMethod() instanceof GrGdkMethod)) {
      return true;
    }

    state = state.put(ClassHint.RESOLVE_CONTEXT, call);

    if ((args.length == 1 || args.length == 2 && placeEqualsLastArg(place, args))) {
      PsiType type = args[0].getType();
      if (type instanceof GrTupleType) {
        return processTypesFromTuple((GrTupleType)type, processor, state, place);
      }
    }
    return processTypesFomArgs(args, processor, state, place);
  }

  private static boolean processTypesFromTuple(@Nonnull GrTupleType type,
                                               @Nonnull PsiScopeProcessor processor,
                                               @Nonnull ResolveState state,
                                               @Nonnull GrClosableBlock place) {
    for (PsiType component : type.getComponentTypes()) {
      PsiType clazz = PsiUtil.substituteTypeParameter(component, CommonClassNames.JAVA_LANG_CLASS, 0, false);
      PsiClass aClass = PsiTypesUtil.getPsiClass(clazz);
      if (aClass != null) {
        if (!processCategoryMethods(place, processor, state, aClass)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean processTypesFomArgs(@Nonnull GrExpression[] args,
                                             @Nonnull PsiScopeProcessor processor,
                                             @Nonnull ResolveState state,
                                             @Nonnull GrClosableBlock place) {
    for (GrExpression arg : args) {
      if (arg instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)arg).resolve();
        if (resolved instanceof PsiClass) {
          if (!processCategoryMethods(place, processor, state, (PsiClass)resolved)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean placeEqualsLastArg(GrClosableBlock place, GrExpression[] args) {
    return args.length > 0 && place.equals(args[args.length - 1]);
  }

  private static boolean placeEqualsSingleClosureArg(GrClosableBlock place, GrClosableBlock[] closures) {
    return closures.length == 1 && place.equals(closures[0]);
  }

  /**
   * @param place         - context of processing
   * @param processor     - processor to use
   * @param categoryClass - category class to process
   * @return
   */
  public static boolean processCategoryMethods(final PsiElement place,
                                               final PsiScopeProcessor processor,
                                               @Nonnull final ResolveState state,
                                               @Nonnull final PsiClass categoryClass) {
    final PsiScopeProcessor delegate = new GrDelegatingScopeProcessorWithHints(processor, null,
                                                                               ClassHint.RESOLVE_KINDS_METHOD) {
      @Override
      public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState delegateState) {
        if (element instanceof PsiMethod && isCategoryMethod((PsiMethod)element, null, null, null)) {
          PsiMethod method = (PsiMethod)element;
          return processor.execute(GrGdkMethodImpl.createGdkMethod(method, false,
                                                                   generateOriginInfo(method)), delegateState);
        }
        return true;
      }
    };
    return categoryClass.processDeclarations(delegate, state, null, place);
  }

  @Nullable
  private static GrMethodCall checkMethodCall(GrClosableBlock place, String methodName) {
    final PsiElement context = place.getContext();
    GrMethodCall call = null;
    if (context instanceof GrMethodCall) {
      call = (GrMethodCall)context;
    }
    else if (context instanceof GrArgumentList) {
      final PsiElement ccontext = context.getContext();
      if (ccontext instanceof GrMethodCall) {
        call = (GrMethodCall)ccontext;
      }
    }
    if (call == null) {
      return null;
    }
    final GrExpression invoked = call.getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression) || !methodName.equals(((GrReferenceExpression)invoked)
                                                                            .getReferenceName())) {
      return null;
    }
    return call;
  }

  /**
   * @param resolveContext is a qualifier of 'resolveContext.with {}'
   */
  public static boolean isInWithContext(PsiElement resolveContext) {
    if (resolveContext instanceof GrExpression) {
      final PsiElement parent = resolveContext.getParent();
      if (parent instanceof GrReferenceExpression && ((GrReferenceExpression)parent).getQualifier() ==
        resolveContext) {
        final PsiElement pparent = parent.getParent();
        if (pparent instanceof GrMethodCall) {
          final PsiMethod method = ((GrMethodCall)pparent).resolveMethod();
          if (method instanceof GrGdkMethod && isWithName(method.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isWithName(String name) {
    return WITH.equals(name) || IDENTITY.equals(name);
  }

  @Nullable
  public static String generateOriginInfo(PsiMethod method) {
    PsiClass cc = method.getContainingClass();
    if (cc == null) {
      return null;
    }
    //'\u2191'
    return "via " + cc.getName();
  }

  public static boolean processMixinToMetaclass(GrStatementOwner run,
                                                final PsiScopeProcessor processor,
                                                ResolveState state,
                                                PsiElement lastParent,
                                                PsiElement place) {
    GrStatement[] statements = run.getStatements();
    for (GrStatement statement : statements) {
      if (statement == lastParent) {
        break;
      }

      final Trinity<PsiClassType, GrReferenceExpression, PsiClass> result = getMixinTypes(statement);

      if (result != null) {
        final PsiClassType subjectType = result.first;
        final GrReferenceExpression qualifier = result.second;
        final PsiClass mixin = result.third;

        final DelegatingScopeProcessor delegate = new MixinMemberContributor.MixinProcessor(processor,
                                                                                            subjectType, qualifier);
        mixin.processDeclarations(delegate, state, null, place);
      }
      else {
        Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>> closureResult = getClosureMixins
          (statement);
        if (closureResult != null) {
          final PsiClassType subjectType = closureResult.first;
          final GrReferenceExpression qualifier = closureResult.second;
          final List<GrMethod> methods = closureResult.third;

          final DelegatingScopeProcessor delegate = new MixinMemberContributor.MixinProcessor(processor,
                                                                                              subjectType, qualifier);
          for (GrMethod method : methods) {
            ResolveUtil.processElement(delegate, method, state);
          }
        }
      }
    }

    return true;
  }

  @Nonnull
  private static GrMethod createMethod(@Nonnull GrClosureSignature signature,
                                       @Nonnull String name,
                                       @Nonnull GrAssignmentExpression statement,
                                       @Nonnull PsiClass closure) {
    final GrLightMethodBuilder builder = new GrLightMethodBuilder(statement.getManager(), name);

    GrClosureParameter[] parameters = signature.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrClosureParameter parameter = parameters[i];
      final String parameterName = parameter.getName() != null ? parameter.getName() : "p" + i;
      final PsiType type = parameter.getType() != null ? parameter.getType() : TypesUtil.getJavaLangObject
        (statement);
      builder.addParameter(parameterName, type, parameter.isOptional());
    }

    builder.setNavigationElement(statement.getLValue());
    builder.setReturnType(signature.getReturnType());
    builder.setContainingClass(closure);
    return builder;
  }

  private static Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>> getClosureMixins(final GrStatement
                                                                                                 statement) {
    if (!(statement instanceof GrAssignmentExpression)) {
      return null;
    }

    final GrAssignmentExpression assignment = (GrAssignmentExpression)statement;
    return LanguageCachedValueUtil.getCachedValue(statement, new CachedValueProvider<Trinity<PsiClassType,
      GrReferenceExpression, List<GrMethod>>>() {
      @Nullable
      @Override
      public Result<Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>>> compute() {

        Pair<PsiClassType, GrReferenceExpression> original = getTypeToMixIn(assignment);
        if (original == null) {
          return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
        }

        final Pair<GrSignature, String> signatures = getTypeToMix(assignment);
        if (signatures == null) {
          return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
        }

        final String name = signatures.second;

        final List<GrMethod> methods = ContainerUtil.newArrayList();
        final PsiClass closure = GroovyPsiManager.getInstance(statement.getProject()).findClassWithCache
          (GroovyCommonClassNames.GROOVY_LANG_CLOSURE, statement.getResolveScope());
        if (closure == null) {
          return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
        }

        signatures.first.accept(new GrSignatureVisitor() {
          @Override
          public void visitClosureSignature(GrClosureSignature signature) {
            super.visitClosureSignature(signature);
            GrMethod method = createMethod(signature, name, assignment, closure);
            methods.add(method);
          }
        });

        return Result.create(Trinity.create(original.first, original.second, methods),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getTypeToMixIn(GrAssignmentExpression assignment) {
    final GrExpression lvalue = assignment.getLValue();
    if (lvalue instanceof GrReferenceExpression) {
      final GrExpression metaClassRef = ((GrReferenceExpression)lvalue).getQualifier();
      if (metaClassRef instanceof GrReferenceExpression && (GrImportUtil.acceptName((GrReferenceElement)
                                                                                      metaClassRef, "metaClass") || GrImportUtil.acceptName(
        (GrReferenceElement)metaClassRef,
        "getMetaClass"))) {
        final PsiElement resolved = ((GrReferenceElement)metaClassRef).resolve();
        if (resolved instanceof PsiMethod && isMetaClassMethod((PsiMethod)resolved)) {
          return getPsiClassFromReference(((GrReferenceExpression)metaClassRef).getQualifier());
        }
      }
    }
    return null;
  }

  @Nullable
  private static Pair<GrSignature, String> getTypeToMix(GrAssignmentExpression assignment) {
    GrExpression mixinRef = assignment.getRValue();
    if (mixinRef == null) {
      return null;
    }

    final PsiType type = mixinRef.getType();
    if (type instanceof GrClosureType) {
      final GrSignature signature = ((GrClosureType)type).getSignature();

      final GrExpression lValue = assignment.getLValue();
      assert lValue instanceof GrReferenceExpression;
      final String name = ((GrReferenceExpression)lValue).getReferenceName();

      return Pair.create(signature, name);
    }

    return null;
  }

  /**
   * @return (type[1] in which methods mixed, reference to type[1], type[2] to mixin)
   */
  @Nullable
  private static Trinity<PsiClassType, GrReferenceExpression, PsiClass> getMixinTypes(final GrStatement statement) {
    if (!(statement instanceof GrMethodCall)) {
      return null;
    }

    return LanguageCachedValueUtil.getCachedValue(statement, new CachedValueProvider<Trinity<PsiClassType,
      GrReferenceExpression, PsiClass>>() {
      @Nullable
      @Override
      public Result<Trinity<PsiClassType, GrReferenceExpression, PsiClass>> compute() {
        GrMethodCall call = (GrMethodCall)statement;

        Pair<PsiClassType, GrReferenceExpression> original = getTypeToMixIn(call);
        if (original == null) {
          return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
        }

        PsiClass mix = getTypeToMix(call);
        if (mix == null) {
          return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
        }

        return Result.create(new Trinity<PsiClassType, GrReferenceExpression, PsiClass>(original.first,
                                                                                        original.second, mix),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static PsiClass getTypeToMix(GrMethodCall call) {
    if (!isSingleExpressionArg(call)) {
      return null;
    }

    GrExpression mixinRef = call.getExpressionArguments()[0];
    if (isClassRef(mixinRef)) {
      mixinRef = ((GrReferenceExpression)mixinRef).getQualifier();
    }

    if (mixinRef instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)mixinRef).resolve();
      if (resolved instanceof PsiClass) {
        return (PsiClass)resolved;
      }
    }

    return null;
  }

  private static boolean isSingleExpressionArg(GrMethodCall call) {
    return call.getExpressionArguments().length == 1 &&
      !PsiImplUtil.hasNamedArguments(call.getArgumentList()) &&
      !PsiImplUtil.hasClosureArguments(call);
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getTypeToMixIn(GrMethodCall methodCall) {
    GrExpression invoked = methodCall.getInvokedExpression();
    if (invoked instanceof GrReferenceExpression && GrImportUtil.acceptName((GrReferenceExpression)invoked,
                                                                            "mixin")) {
      PsiElement resolved = ((GrReferenceExpression)invoked).resolve();
      if (resolved instanceof PsiMethod && isMixinMethod((PsiMethod)resolved)) {
        GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
        Pair<PsiClassType, GrReferenceExpression> type = getPsiClassFromReference(qualifier);
        if (type != null) {
          return type;
        }
        if (qualifier == null) {
          qualifier = PsiImplUtil.getRuntimeQualifier((GrReferenceExpression)invoked);
        }
        if (qualifier != null && isMetaClass(qualifier.getType())) {
          if (qualifier instanceof GrMethodCall) {
            qualifier = ((GrMethodCall)qualifier).getInvokedExpression();
          }

          if (qualifier instanceof GrReferenceExpression) {
            GrExpression qqualifier = ((GrReferenceExpression)qualifier).getQualifier();
            if (qqualifier != null) {
              Pair<PsiClassType, GrReferenceExpression> type1 = getPsiClassFromReference(qqualifier);
              if (type1 != null) {
                return type1;
              }
            }
            else {
              PsiType qtype = PsiImplUtil.getQualifierType((GrReferenceExpression)qualifier);
              if (qtype instanceof PsiClassType && ((PsiClassType)qtype).resolve() != null) {
                return Pair.create((PsiClassType)qtype, (GrReferenceExpression)qualifier);
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isMixinMethod(@Nonnull PsiMethod method) {
    if (method instanceof GrGdkMethod) {
      method = ((GrGdkMethod)method).getStaticMethod();
    }
    PsiClass containingClass = method.getContainingClass();
    String name = method.getName();
    return "mixin".equals(name) && containingClass != null && GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals
      (containingClass.getQualifiedName());
  }

  private static boolean isMetaClassMethod(@Nonnull PsiMethod method) {
    if (method instanceof GrGdkMethod) {
      method = ((GrGdkMethod)method).getStaticMethod();
    }
    PsiClass containingClass = method.getContainingClass();
    String name = method.getName();
    return "getMetaClass".equals(name) &&
      containingClass != null &&
      (method.getParameterList().getParametersCount() == 0 ^ GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
        .equals(containingClass.getQualifiedName()));
  }

  private static boolean isMetaClass(PsiType qualifierType) {
    return qualifierType != null && qualifierType.equalsToText(GroovyCommonClassNames.GROOVY_LANG_META_CLASS);
  }

  private static boolean isClassRef(GrExpression mixinRef) {
    return mixinRef instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)mixinRef)
                                                                         .getReferenceName());
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getPsiClassFromReference(GrExpression ref) {
    if (isClassRef(ref)) {
      ref = ((GrReferenceExpression)ref).getQualifier();
    }
    if (ref instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)ref).resolve();
      if (resolved instanceof PsiClass) {
        PsiType type = ref.getType();
        LOG.assertTrue(type instanceof PsiClassType, "reference resolved into PsiClass should have " +
          "PsiClassType");
        return Pair.create((PsiClassType)type, (GrReferenceExpression)ref);
      }
    }
    return null;
  }

  public static boolean isCategoryMethod(@Nonnull PsiMethod method,
                                         @Nullable PsiType qualifierType,
                                         @Nullable PsiElement place,
                                         @Nullable PsiSubstitutor substitutor) {
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) {
      return false;
    }

    if (qualifierType == null) {
      return true;
    }

    PsiType selfType = parameters[0].getType();
    if (selfType instanceof PsiPrimitiveType) {
      return false;
    }

    if (substitutor != null) {
      selfType = substitutor.substitute(selfType);
    }

    if (selfType instanceof PsiClassType &&
      ((PsiClassType)selfType).rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS) &&
      place instanceof GrReferenceExpression &&
      ((GrReferenceExpression)place).resolve() instanceof PsiClass) {   // ClassType.categoryMethod()  where categoryMethod(Class<> cl, ...)
      final GlobalSearchScope scope = method.getResolveScope();
      final Project project = method.getProject();
      return TypesUtil.isAssignableByMethodCallConversion(selfType, TypesUtil.createJavaLangClassType
        (qualifierType, project, scope), method);
    }
    return TypesUtil.isAssignableByMethodCallConversion(selfType, qualifierType, method);
  }

  @Nullable
  public static PsiClassType getCategoryType(@Nonnull final PsiClass categoryAnnotationOwner) {
    return LanguageCachedValueUtil.getCachedValue(categoryAnnotationOwner, new CachedValueProvider<PsiClassType>() {
      @Override
      public Result<PsiClassType> compute() {
        return Result.create(inferCategoryType(categoryAnnotationOwner),
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }

      @Nullable
      private PsiClassType inferCategoryType(final PsiClass aClass) {
        return RecursionManager.doPreventingRecursion(aClass, true, new Supplier<PsiClassType>() {
          @Nullable
          @Override
          public PsiClassType get() {
            final PsiModifierList modifierList = aClass.getModifierList();
            if (modifierList == null) {
              return null;
            }

            final PsiAnnotation annotation = modifierList.findAnnotation(GroovyCommonClassNames
                                                                           .GROOVY_LANG_CATEGORY);
            if (annotation == null) {
              return null;
            }

            PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (!(value instanceof GrReferenceExpression)) {
              return null;
            }

            if ("class".equals(((GrReferenceExpression)value).getReferenceName())) {
              value = ((GrReferenceExpression)value).getQualifier();
            }
            if (!(value instanceof GrReferenceExpression)) {
              return null;
            }

            final PsiElement resolved = ((GrReferenceExpression)value).resolve();
            if (!(resolved instanceof PsiClass)) {
              return null;
            }

            String className = ((PsiClass)resolved).getQualifiedName();
            if (className == null) {
              className = ((PsiClass)resolved).getName();
            }
            if (className == null) {
              return null;
            }

            return JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeByFQClassName(className,
                                                                                                resolved.getResolveScope());
          }
        });
      }
    });
  }

  public static boolean isWithOrIdentity(@Nonnull GroovyResolveResult result) {
    PsiElement element = result.getElement();

    if (element instanceof PsiMethod && isWithName(((PsiMethod)element).getName())) {
      if (element instanceof GrGdkMethod) {
        element = ((GrGdkMethod)element).getStaticMethod();
      }
      final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      if (containingClass != null) {
        if (GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName())) {
          return true;
        }
      }
    }
    return false;
  }
}
