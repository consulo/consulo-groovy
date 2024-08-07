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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.impl.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.java.language.psi.*;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.Consumer;

/**
 * Created by Max Medvedev on 25/04/14
 */
public class CompleteCodeReferenceElement {
  private static final Logger LOG = Logger.getInstance(CompleteCodeReferenceElement.class);

  private final GrCodeReferenceElementImpl myRef;
  private final Consumer<LookupElement> myConsumer;
  private final PrefixMatcher myMatcher;

  private CompleteCodeReferenceElement(@Nonnull GrCodeReferenceElementImpl ref,
                                       @Nonnull Consumer<LookupElement> consumer,
                                       @Nonnull PrefixMatcher matcher) {
    myRef = ref;
    myConsumer = consumer;
    myMatcher = matcher;
  }

  public static void processVariants(@Nonnull GrCodeReferenceElementImpl ref,
                                     @Nonnull Consumer<LookupElement> consumer,
                                     @Nonnull PrefixMatcher matcher) {
    new CompleteCodeReferenceElement(ref, consumer, matcher).processVariantsImpl();
  }

  private void feedLookupElements(@Nonnull PsiNamedElement psi, boolean afterNew) {
    GroovyResolveResultImpl candidate = new GroovyResolveResultImpl(psi, true);
    List<? extends LookupElement> elements = GroovyCompletionUtil.createLookupElements(candidate, afterNew, myMatcher, null);
    for (LookupElement element : elements) {
      myConsumer.accept(element);
    }
  }

  public void processVariantsImpl() {
    boolean afterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(myRef);

    switch (myRef.getKind(true)) {
      case STATIC_MEMBER_FQ: {
        final GrCodeReferenceElement qualifier = myRef.getQualifier();
        if (qualifier != null) {
          final PsiElement resolve = qualifier.resolve();
          if (resolve instanceof PsiClass) {
            final PsiClass clazz = (PsiClass)resolve;

            for (PsiField field : clazz.getFields()) {
              if (field.hasModifierProperty(PsiModifier.STATIC)) {
                feedLookupElements(field, afterNew);
              }
            }

            for (PsiMethod method : clazz.getMethods()) {
              if (method.hasModifierProperty(PsiModifier.STATIC)) {
                feedLookupElements(method, afterNew);
              }
            }

            for (PsiClass inner : clazz.getInnerClasses()) {
              if (inner.hasModifierProperty(PsiModifier.STATIC)) {
                feedLookupElements(inner, afterNew);
              }
            }
            return;
          }
        }
      }
      // fall through

      case PACKAGE_FQ:
      case CLASS_FQ:
      case CLASS_OR_PACKAGE_FQ: {
        final String refText = PsiUtil.getQualifiedReferenceText(myRef);
        LOG.assertTrue(refText != null, myRef.getText());

        String parentPackageFQName = StringUtil.getPackageName(refText);
        final PsiJavaPackage parentPackage = JavaPsiFacade.getInstance(myRef.getProject()).findPackage(parentPackageFQName);
        if (parentPackage != null) {
          final GlobalSearchScope scope = myRef.getResolveScope();
          if (myRef.getKind(true) == GrCodeReferenceElementImpl.ReferenceKind.PACKAGE_FQ) {
            for (PsiPackage aPackage : parentPackage.getSubPackages(scope)) {
              feedLookupElements(aPackage, afterNew);
            }
            return;
          }

          if (myRef.getKind(true) == GrCodeReferenceElementImpl.ReferenceKind.CLASS_FQ) {
            for (PsiClass aClass : parentPackage.getClasses(scope)) {
              feedLookupElements(aClass, afterNew);
            }
            return;
          }

          for (PsiPackage aPackage : parentPackage.getSubPackages(scope)) {
            feedLookupElements(aPackage, afterNew);
          }
          for (PsiClass aClass : parentPackage.getClasses(scope)) {
            feedLookupElements(aClass, afterNew);
          }
          return;
        }
      }

      case CLASS_OR_PACKAGE:
      case CLASS_IN_QUALIFIED_NEW:
      case CLASS: {
        GrCodeReferenceElement qualifier = myRef.getQualifier();
        if (qualifier != null) {
          PsiElement qualifierResolved = qualifier.resolve();
          if (qualifierResolved instanceof PsiJavaPackage) {
            PsiJavaPackage aPackage = (PsiJavaPackage)qualifierResolved;
            for (PsiClass aClass : aPackage.getClasses(myRef.getResolveScope())) {
              feedLookupElements(aClass, afterNew);
            }
            if (myRef.getKind(true) == GrCodeReferenceElementImpl.ReferenceKind.CLASS) return;

            for (PsiPackage subpackage : aPackage.getSubPackages(myRef.getResolveScope())) {
              feedLookupElements(subpackage, afterNew);
            }
          }
          else if (qualifierResolved instanceof PsiClass) {
            for (PsiClass aClass : ((PsiClass)qualifierResolved).getInnerClasses()) {
              feedLookupElements(aClass, afterNew);
            }
          }
        }
        else {
          ResolverProcessorImpl classProcessor = CompletionProcessor.createClassCompletionProcessor(myRef);
          processTypeParametersFromUnfinishedMethodOrField(classProcessor);

          ResolveUtil.treeWalkUp(myRef, classProcessor, false);

          for (LookupElement o : GroovyCompletionUtil.getCompletionVariants(classProcessor.getCandidates(), afterNew, myMatcher, myRef)) {
            myConsumer.accept(o);
          }
        }
      }
    }
  }

  private void processTypeParametersFromUnfinishedMethodOrField(@Nonnull ResolverProcessorImpl processor) {

    final PsiElement candidate = findTypeParameterListCandidate();

    if (candidate instanceof GrTypeParameterList) {
      for (GrTypeParameter p : ((GrTypeParameterList)candidate).getTypeParameters()) {
        ResolveUtil.processElement(processor, p, ResolveState.initial());
      }
    }
  }

  @Nullable
  private PsiElement findTypeParameterListCandidate() {
    final GrTypeElement typeElement = getRootTypeElement();
    if (typeElement == null) return null;

    if (typeElement.getParent() instanceof GrTypeDefinitionBody) {
      return PsiUtil.skipWhitespacesAndComments(typeElement.getPrevSibling(), false);
    }

    if (typeElement.getParent() instanceof GrVariableDeclaration) {
      final PsiElement errorElement = PsiUtil.skipWhitespacesAndComments(typeElement.getPrevSibling(), false);
      if (errorElement instanceof PsiErrorElement) {
        return errorElement.getFirstChild();
      }
    }

    return null;
  }

  @Nullable
  private GrTypeElement getRootTypeElement() {
    PsiElement parent = myRef.getParent();
    while (isTypeElementChild(parent)) {
      if (parent instanceof GrTypeElement && !isTypeElementChild(parent.getParent())) return (GrTypeElement)parent;
      parent = parent.getParent();
    }

    return null;
  }

  private static boolean isTypeElementChild(@Nullable PsiElement element) {
    return element instanceof GrCodeReferenceElement || element instanceof GrTypeArgumentList || element instanceof GrTypeElement;
  }
}
