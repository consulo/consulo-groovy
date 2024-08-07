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
package org.jetbrains.plugins.groovy.impl.codeInspection.untypedUnresolvedAccess;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.CollectConsumer;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixUpdater;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.pom.PomDeclarationSearcher;
import consulo.language.pom.PomTarget;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.impl.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.impl.findUsages.MissingMethodAndPropertyUtil;
import org.jetbrains.plugins.groovy.impl.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrScopeProcessorWithHints;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Max Medvedev on 21/03/14
 */
public class GrUnresolvedAccessChecker {
  public static final Logger LOG = Logger.getInstance(GrUnresolvedAccessChecker.class);

  private static final LightCacheKey<Map<String, Boolean>> GROOVY_OBJECT_METHODS_CACHE = new LightCacheKey<>() {
      @Override
      protected long getModificationCount(PsiElement holder) {
        return holder.getManager().getModificationTracker().getModificationCount();
      }
    };

  private final HighlightDisplayKey myDisplayKey;
  private final boolean myInspectionEnabled;
  private final GrUnresolvedAccessInspectionState myInspectionState;

  public GrUnresolvedAccessChecker(@Nonnull GroovyFileBase file, @Nonnull Project project) {
    myInspectionEnabled = GrUnresolvedAccessInspection.isInspectionEnabled(file, project);
    myInspectionState = myInspectionEnabled ? GrUnresolvedAccessInspection.getInstanceState(file, project) : null;
    myDisplayKey = GrUnresolvedAccessInspection.findDisplayKey();
  }

  @Nullable
  @RequiredReadAction
  public HighlightInfo checkCodeReferenceElement(GrCodeReferenceElement refElement) {
    HighlightInfo.Builder builder = checkCodeRefInner(refElement);
    return builder == null ? null : builder.create();
  }

  @Nullable
  @RequiredReadAction
  public List<HighlightInfo> checkReferenceExpression(GrReferenceExpression ref) {
    List<HighlightInfo.Builder> builders = checkRefInner(ref);
    return builders == null ? null : ContainerUtil.mapNotNull(builders, HighlightInfo.Builder::create);
  }

  @Nullable
  @RequiredReadAction
  private HighlightInfo.Builder checkCodeRefInner(GrCodeReferenceElement refElement) {
    if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class) != null) {
      return null;
    }

    PsiElement nameElement = refElement.getReferenceNameElement();
    if (nameElement == null) {
      return null;
    }

    if (isResolvedStaticImport(refElement)) {
      return null;
    }

    GroovyResolveResult resolveResult = refElement.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    if (!(refElement.getParent() instanceof GrPackageDefinition) && resolved == null) {
      String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(nameElement).descriptionAndTooltip(message);

      // todo implement for nested classes
      registerCreateClassByTypeFix(refElement, builder, myDisplayKey);
      registerAddImportFixes(refElement, builder, myDisplayKey);
      UnresolvedReferenceQuickFixUpdater.getInstance(refElement.getProject()).registerQuickFixesLater(refElement, builder);
      List<LocalQuickFix> fixes = QuickFixFactory.getInstance().registerOrderEntryFixes(refElement);
      if (fixes != null) {
        for (LocalQuickFix fix : fixes) {
          builder.registerFix((IntentionAction)fix, null, null, null, null);
        }
      }

      return builder;
    }

    if (refElement.getParent() instanceof GrNewExpression) {

      boolean inStaticContext = GrStaticChecker.isInStaticContext(refElement);

      if (!inStaticContext && GrUnresolvedAccessInspection.isSuppressed(refElement)) {
        return null;
      }

      if (!inStaticContext) {
        if (!myInspectionEnabled) {
          return null;
        }
        assert myInspectionState != null;
        if (!myInspectionState.myHighlightInnerClasses) {
          return null;
        }
      }

      GrNewExpression newExpression = (GrNewExpression)refElement.getParent();
      if (resolved instanceof PsiClass) {
        PsiClass clazz = (PsiClass)resolved;
        if (newExpression.getQualifier() == null) {
          final PsiClass outerClass = clazz.getContainingClass();
          if (com.intellij.java.language.psi.util.PsiUtil.isInnerClass(clazz) &&
            outerClass != null &&
            newExpression.getArgumentList() != null &&
            !PsiUtil.hasEnclosingInstanceInScope(outerClass, newExpression, true) &&
            !hasEnclosingInstanceInArgList(newExpression.getArgumentList(), outerClass)) {
            String qname = clazz.getQualifiedName();
            LOG.assertTrue(qname != null, clazz.getText());
            return createAnnotationForRef(refElement, inStaticContext, GroovyBundle.message("cannot" +
                                                                                              ".reference.non.static", qname));
          }
        }
      }
    }

    return null;
  }

  private static boolean hasEnclosingInstanceInArgList(@Nonnull GrArgumentList list,
                                                       @Nonnull PsiClass enclosingClass) {
    if (PsiImplUtil.hasNamedArguments(list)) {
      return false;
    }

    GrExpression[] args = list.getExpressionArguments();
    if (args.length == 0) {
      return false;
    }

    PsiType type = args[0].getType();
    PsiClassType enclosingClassType = JavaPsiFacade.getElementFactory(list.getProject()).createType
      (enclosingClass);
    return TypesUtil.isAssignableByMethodCallConversion(enclosingClassType, type, list);
  }

  @Nullable
  @RequiredReadAction
  private List<HighlightInfo.Builder> checkRefInner(GrReferenceExpression ref) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    if (refNameElement == null) {
      return null;
    }

    boolean inStaticContext = PsiUtil.isCompileStatic(ref) || GrStaticChecker.isPropertyAccessInStaticMethod(ref);
    GroovyResolveResult resolveResult = getBestResolveResult(ref);

    if (resolveResult.getElement() != null) {
      if (!GrUnresolvedAccessInspection.isInspectionEnabled(ref.getContainingFile(), ref.getProject())) {
        return null;
      }

      if (!isStaticOk(resolveResult)) {
        String message = GroovyBundle.message("cannot.reference.non.static", ref.getReferenceName());
        HighlightInfo.Builder builder = createAnnotationForRef(ref, inStaticContext, message);
        return builder == null ? null : List.of(builder);
      }

      return null;
    }

    if (ResolveUtil.isKeyOfMap(ref) || ResolveUtil.isClassReference(ref)) {
      return null;
    }

    if (!inStaticContext) {
      if (!GrUnresolvedAccessInspection.isInspectionEnabled(ref.getContainingFile(), ref.getProject())) {
        return null;
      }
      assert myInspectionState != null;

      if (!myInspectionState.myHighlightIfGroovyObjectOverridden && areGroovyObjectMethodsOverridden(ref)) {
        return null;
      }
      if (!myInspectionState.myHighlightIfMissingMethodsDeclared && areMissingMethodsDeclared(ref)) {
        return null;
      }

      if (GrUnresolvedAccessInspection.isSuppressed(ref)) {
        return null;
      }
    }

    if (inStaticContext || shouldHighlightAsUnresolved(ref)) {
      HighlightInfo.Builder builder = createAnnotationForRef(ref, inStaticContext, GroovyBundle.message("cannot.resolve",
                                                                                                        ref.getReferenceName()));
      if (builder == null) {
        return null;
      }

      List<HighlightInfo.Builder> result = new ArrayList<>();
      result.add(builder);
      if (ref.getParent() instanceof GrMethodCall) {
        ContainerUtil.addIfNotNull(result, registerStaticImportFix(ref, myDisplayKey));
      }
      else {
        registerCreateClassByTypeFix(ref, builder, myDisplayKey);
        registerAddImportFixes(ref, builder, myDisplayKey);
      }

      registerReferenceFixes(ref, builder, inStaticContext, myDisplayKey);
      UnresolvedReferenceQuickFixUpdater.getInstance(ref.getProject()).registerQuickFixesLater(ref, builder);
      List<LocalQuickFix> fixes = QuickFixFactory.getInstance().registerOrderEntryFixes(ref);
      if (fixes != null) {
        for (LocalQuickFix fix : fixes) {
          builder.registerFix((IntentionAction)fix, null, null, null, null);
        }
      }
      return result;
    }

    return null;
  }

  private static boolean areMissingMethodsDeclared(GrReferenceExpression ref) {
    PsiType qualifierType = PsiImplUtil.getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) {
      return false;
    }

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) {
      return false;
    }

    if (ref.getParent() instanceof GrCall) {
      PsiMethod[] found = resolved.findMethodsByName("methodMissing", true);
      for (PsiMethod method : found) {
        if (MissingMethodAndPropertyUtil.isMethodMissing(method)) {
          return true;
        }
      }
    }
    else {
      PsiMethod[] found = resolved.findMethodsByName("propertyMissing", true);
      for (PsiMethod method : found) {
        if (MissingMethodAndPropertyUtil.isPropertyMissing(method)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean areGroovyObjectMethodsOverridden(GrReferenceExpression ref) {
    PsiMethod patternMethod = findPatternMethod(ref);
    if (patternMethod == null) {
      return false;
    }

    GrExpression qualifier = ref.getQualifier();
    if (qualifier != null) {
      return checkGroovyObjectMethodsByQualifier(ref, patternMethod);
    }
    else {
      return checkMethodInPlace(ref, patternMethod);
    }
  }

  private static boolean checkMethodInPlace(GrReferenceExpression ref, PsiMethod patternMethod) {
    PsiElement container = PsiTreeUtil.getParentOfType(ref, GrClosableBlock.class, PsiMember.class, PsiFile.class);
    assert container != null;
    return checkContainer(patternMethod, container);
  }

  private static boolean checkContainer(@Nonnull final PsiMethod patternMethod, @Nonnull PsiElement container) {
    final String name = patternMethod.getName();

    Map<String, Boolean> cached = GROOVY_OBJECT_METHODS_CACHE.getCachedValue(container);
    if (cached == null) {
      GROOVY_OBJECT_METHODS_CACHE.putCachedValue(container, cached = ContainerUtil.newConcurrentMap());
    }

    Boolean cachedResult = cached.get(name);
    if (cachedResult != null) {
      return cachedResult.booleanValue();
    }

    boolean result = doCheckContainer(patternMethod, container, name);
    cached.put(name, result);

    return result;
  }

  private static boolean doCheckContainer(final PsiMethod patternMethod, PsiElement container, final String name) {
    final Ref<Boolean> result = new Ref<Boolean>(false);
    PsiScopeProcessor processor = new GrScopeProcessorWithHints(name, ClassHint.RESOLVE_KINDS_METHOD) {
      @Override
      public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        if (element instanceof PsiMethod &&
          name.equals(((PsiMethod)element).getName()) &&
          patternMethod.getParameterList().getParametersCount() == ((PsiMethod)element)
            .getParameterList().getParametersCount() &&
          isNotFromGroovyObject((PsiMethod)element)) {
          result.set(true);
          return false;
        }
        return true;
      }
    };
    ResolveUtil.treeWalkUp(container, processor, true);
    return result.get();
  }

  private static boolean checkGroovyObjectMethodsByQualifier(GrReferenceExpression ref, PsiMethod patternMethod) {
    PsiType qualifierType = PsiImplUtil.getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) {
      return false;
    }

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) {
      return false;
    }

    PsiMethod found = resolved.findMethodBySignature(patternMethod, true);
    if (found == null) {
      return false;
    }

    return isNotFromGroovyObject(found);
  }

  private static boolean isNotFromGroovyObject(@Nonnull PsiMethod found) {
    PsiClass aClass = found.getContainingClass();
    if (aClass == null) {
      return false;
    }
    String qname = aClass.getQualifiedName();
    if (GroovyCommonClassNames.GROOVY_OBJECT.equals(qname)) {
      return false;
    }
    if (GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) {
      return false;
    }
    return true;
  }

  @Nullable
  private static PsiMethod findPatternMethod(@Nonnull GrReferenceExpression ref) {
    PsiClass groovyObject = GroovyPsiManager.getInstance(ref.getProject()).findClassWithCache
      (GroovyCommonClassNames.GROOVY_OBJECT, ref.getResolveScope());
    if (groovyObject == null) {
      return null;
    }

    String methodName = ref.getParent() instanceof GrCall ? "invokeMethod" : PsiUtil.isLValue(ref) ? "setProperty"
      : "getProperty";

    PsiMethod[] patternMethods = groovyObject.findMethodsByName(methodName, false);
    if (patternMethods.length != 1) {
      return null;
    }
    return patternMethods[0];
  }

  private static boolean isResolvedStaticImport(GrCodeReferenceElement refElement) {
    final PsiElement parent = refElement.getParent();
    return parent instanceof GrImportStatement &&
      ((GrImportStatement)parent).isStatic() &&
      refElement.multiResolve(false).length > 0;
  }

  private static boolean isStaticOk(GroovyResolveResult resolveResult) {
    if (resolveResult.isStaticsOK()) {
      return true;
    }

    PsiElement resolved = resolveResult.getElement();
    LOG.assertTrue(resolved != null);
    LOG.assertTrue(resolved instanceof PsiModifierListOwner, resolved + " : " + resolved.getText());

    return ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC);
  }

  @Nonnull
  private static GroovyResolveResult getBestResolveResult(GrReferenceExpression ref) {
    GroovyResolveResult[] results = ref.multiResolve(false);
    if (results.length == 0) {
      return GroovyResolveResult.EMPTY_RESULT;
    }
    if (results.length == 1) {
      return results[0];
    }

    for (GroovyResolveResult result : results) {
      if (result.isAccessible() && result.isStaticsOK()) {
        return result;
      }
    }

    for (GroovyResolveResult result : results) {
      if (result.isStaticsOK()) {
        return result;
      }
    }

    return results[0];
  }

  @Nullable
  @RequiredReadAction
  private static HighlightInfo.Builder createAnnotationForRef(@Nonnull GrReferenceElement ref,
                                                              boolean strongError,
                                                              @Nonnull String message) {
    HighlightDisplayLevel displayLevel = strongError ? HighlightDisplayLevel.ERROR : GrUnresolvedAccessInspection
      .getHighlightDisplayLevel(ref.getProject(), ref);
    return GrInspectionUtil.createAnnotationForRef(ref, displayLevel, message);
  }

  @Nullable
  @RequiredReadAction
  private static HighlightInfo.Builder registerStaticImportFix(@Nonnull GrReferenceExpression referenceExpression,
                                                               @Nullable final HighlightDisplayKey key) {
    final String referenceName = referenceExpression.getReferenceName();
    if (StringUtil.isEmpty(referenceName)) {
      return null;
    }
    if (referenceExpression.getQualifier() != null) {
      return null;
    }

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(referenceExpression.getParent());
    registerQuickFixAction(builder,
                           GroovyQuickFixFactory.getInstance()
                                                .createGroovyStaticImportMethodFix((GrMethodCall)referenceExpression.getParent()),
                           key);
    return builder;
  }

  private static void registerReferenceFixes(GrReferenceExpression refExpr,
                                             HighlightInfo.Builder builder,
                                             boolean compileStatic,
                                             final HighlightDisplayKey key) {
    PsiClass targetClass = QuickfixUtil.findTargetClass(refExpr, compileStatic);
    if (targetClass == null) {
      return;
    }

    if (!compileStatic) {
      addDynamicAnnotation(builder, refExpr, key);
    }

    if (!(targetClass instanceof SyntheticElement) || (targetClass instanceof GroovyScriptClass)) {
      registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createCreateFieldFromUsageFix(refExpr), key);

      if (PsiUtil.isAccessedForReading(refExpr)) {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createCreateGetterFromUsageFix(refExpr, targetClass), key);
      }
      if (PsiUtil.isLValue(refExpr)) {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createCreateSetterFromUsageFix(refExpr), key);
      }

      if (refExpr.getParent() instanceof GrCall && refExpr.getParent() instanceof GrExpression) {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createCreateMethodFromUsageFix(refExpr), key);
      }
    }

    if (!refExpr.isQualified()) {
      GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(refExpr, GrVariableDeclarationOwner.class);
      if (!(owner instanceof GroovyFileBase) || ((GroovyFileBase)owner).isScript()) {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createCreateLocalVariableFromUsageFix(refExpr, owner), key);
      }
      if (PsiTreeUtil.getParentOfType(refExpr, GrMethod.class) != null) {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createCreateParameterFromUsageFix(refExpr), key);
      }
    }
  }

  private static void addDynamicAnnotation(HighlightInfo.Builder builder,
                                           GrReferenceExpression referenceExpression,
                                           HighlightDisplayKey key) {
    final PsiFile containingFile = referenceExpression.getContainingFile();
    if (containingFile != null) {
      VirtualFile file = containingFile.getVirtualFile();
      if (file == null) {
        return;
      }
    }
    else {
      return;
    }

    if (PsiUtil.isCall(referenceExpression)) {
      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
      if (argumentTypes != null) {
        registerQuickFixAction(builder, referenceExpression.getTextRange(),
                               GroovyQuickFixFactory.getInstance().createDynamicMethodFix(referenceExpression,
                                                                                          argumentTypes), key);
      }
    }
    else {
      registerQuickFixAction(builder, referenceExpression.getTextRange(),
                             GroovyQuickFixFactory.getInstance().createDynamicPropertyFix(referenceExpression), key);
    }
  }

  private static void registerAddImportFixes(GrReferenceElement refElement,
                                             @Nonnull HighlightInfo.Builder builder,
                                             HighlightDisplayKey key) {
    final String referenceName = refElement.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName)) {
      return;
    }
    if (!(refElement instanceof GrCodeReferenceElement) && Character.isLowerCase(referenceName.charAt(0))) {
      return;
    }
    if (refElement.getQualifier() != null) {
      return;
    }

    registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createGroovyAddImportAction(refElement), key);
  }

  private static void registerCreateClassByTypeFix(@Nonnull GrReferenceElement refElement,
                                                   @Nonnull HighlightInfo.Builder builder,
                                                   final HighlightDisplayKey key) {
    GrPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
    if (packageDefinition != null) {
      return;
    }

    PsiElement parent = refElement.getParent();
    if (parent instanceof GrNewExpression && refElement.getManager().areElementsEquivalent(((GrNewExpression)
      parent).getReferenceElement(), refElement)) {
      registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createClassFromNewAction((GrNewExpression)parent), key);
    }
    else if (canBeClassOrPackage(refElement)) {
      if (shouldBeInterface(refElement)) {
        registerQuickFixAction(builder,
                               GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.INTERFACE),
                               key);
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.TRAIT), key);
      }
      else if (shouldBeClass(refElement)) {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.CLASS), key);
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.ENUM), key);
      }
      else if (shouldBeAnnotation(refElement)) {
        registerQuickFixAction(builder,
                               GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.ANNOTATION),
                               key);
      }
      else {
        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.CLASS), key);
        registerQuickFixAction(builder,
                               GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.INTERFACE),
                               key);

        if (!refElement.isQualified() || resolvesToGroovy(refElement.getQualifier())) {
          registerQuickFixAction(builder,
                                 GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.TRAIT),
                                 key);
        }

        registerQuickFixAction(builder, GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.ENUM), key);
        registerQuickFixAction(builder,
                               GroovyQuickFixFactory.getInstance().createClassFixAction(refElement, GrCreateClassKind.ANNOTATION),
                               key);
      }
    }
  }

  private static void registerQuickFixAction(HighlightInfo.Builder builder, IntentionAction action, HighlightDisplayKey key) {
    builder.registerFix(action, List.of(), null, null, key);
  }

  private static void registerQuickFixAction(HighlightInfo.Builder builder,
                                             TextRange textRange,
                                             IntentionAction action,
                                             HighlightDisplayKey key) {
    builder.registerFix(action, List.of(), null, textRange, key);
  }

  private static boolean resolvesToGroovy(PsiElement qualifier) {
    if (qualifier instanceof GrReferenceElement) {
      return ((GrReferenceElement)qualifier).resolve() instanceof GroovyPsiElement;
    }
    if (qualifier instanceof GrExpression) {
      PsiType type = ((GrExpression)qualifier).getType();
      if (type instanceof PsiClassType) {
        PsiClass resolved = ((PsiClassType)type).resolve();
        return resolved instanceof GroovyPsiElement;
      }
    }
    return false;
  }

  private static boolean canBeClassOrPackage(@Nonnull GrReferenceElement refElement) {
    return !(refElement instanceof GrReferenceExpression) || ResolveUtil.canBeClassOrPackage(
      (GrReferenceExpression)refElement);
  }

  private static boolean shouldBeAnnotation(GrReferenceElement element) {
    return element.getParent() instanceof GrAnnotation;
  }

  private static boolean shouldBeInterface(GrReferenceElement myRefElement) {
    PsiElement parent = myRefElement.getParent();
    return parent instanceof GrImplementsClause || parent instanceof GrExtendsClause && parent.getParent()
      instanceof GrInterfaceDefinition;
  }

  private static boolean shouldBeClass(GrReferenceElement myRefElement) {
    PsiElement parent = myRefElement.getParent();
    return parent instanceof GrExtendsClause && !(parent.getParent() instanceof GrInterfaceDefinition);
  }

  private static boolean shouldHighlightAsUnresolved(@Nonnull GrReferenceExpression referenceExpression) {
    if (GrHighlightUtil.isDeclarationAssignment(referenceExpression)) {
      return false;
    }

    GrExpression qualifier = referenceExpression.getQualifier();
    if (qualifier != null && qualifier.getType() == null && !isRefToPackage(qualifier)) {
      return false;
    }

    if (qualifier != null &&
      referenceExpression.getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER &&
      referenceExpression.multiResolve(false).length > 0) {
      return false;
    }

    if (!GroovyUnresolvedHighlightFilter.shouldHighlight(referenceExpression)) {
      return false;
    }

    CollectConsumer<PomTarget> consumer = new CollectConsumer<PomTarget>();
    for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
      searcher.findDeclarationsAt(referenceExpression, 0, consumer);
      if (!consumer.getResult().isEmpty()) {
        return false;
      }
    }

    return true;
  }

  private static boolean isRefToPackage(GrExpression expr) {
    return expr instanceof GrReferenceExpression && ((GrReferenceExpression)expr).resolve() instanceof PsiPackage;
  }
}
