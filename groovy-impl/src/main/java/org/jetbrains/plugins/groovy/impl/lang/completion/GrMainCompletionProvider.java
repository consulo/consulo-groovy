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

import com.intellij.java.impl.codeInsight.completion.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.impl.psi.LightElement;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.EmptyRunnable;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import consulo.util.lang.function.PairConsumer;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.inline.InlineMethodConflictSolver;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Consumer;

/**
 * Created by Max Medvedev on 14/05/14
 */
public class GrMainCompletionProvider implements CompletionProvider {
  public static final ElementPattern<PsiElement> AFTER_AT = PlatformPatterns.psiElement().afterLeaf("@");
  public static final ElementPattern<PsiElement> IN_CATCH_TYPE =
    PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement().withText("(").withParent(GrCatchClause.class));

  private static void addUnfinishedMethodTypeParameters(@Nonnull PsiElement position, @Nonnull CompletionResultSet result) {
    final GrTypeParameterList candidate = findTypeParameterListCandidate(position);

    if (candidate != null) {
      for (GrTypeParameter p : candidate.getTypeParameters()) {
        result.addElement(new JavaPsiClassReferenceElement(p));
      }
    }
  }

  private static void suggestVariableNames(PsiElement context, CompletionResultSet result) {
    final PsiElement parent = context.getParent();
    if (GroovyCompletionUtil.isWildcardCompletion(context)) {
      return;
    }
    if (parent instanceof GrVariable) {
      final GrVariable variable = (GrVariable)parent;
      if (context.equals(variable.getNameIdentifierGroovy())) {
        final PsiType type = variable.getTypeGroovy();
        if (type != null) {
          final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.getProject());
          VariableKind kind =
            variable instanceof GrParameter ? VariableKind.PARAMETER : variable instanceof GrField ? VariableKind.FIELD : VariableKind.LOCAL_VARIABLE;
          SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(kind, null, null, type);
          String[] names = suggestedNameInfo.names;
          if (names.length > 0) {
            String name = names[0];
            String newName = InlineMethodConflictSolver.suggestNewName(name, null, parent);
            if (!name.equals(newName)) {
              result.addElement(LookupElementBuilder.create(newName));
              return;
            }
          }
          for (String name : names) {
            result.addElement(LookupElementBuilder.create(name));
          }
        }

        GrExpression initializer = variable.getInitializerGroovy();
        if (initializer != null) {
          for (String name : GroovyNameSuggestionUtil.suggestVariableNames(initializer,
                                                                           new DefaultGroovyVariableNameValidator(variable),
                                                                           variable.hasModifierProperty(PsiModifier.STATIC))) {
            result.addElement(LookupElementBuilder.create(name));
          }
        }
      }
    }
  }

  @Nullable
  private static GrReferenceElement findGroovyReference(@Nonnull PsiElement position) {
    final PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceElement) {
      return (GrReferenceElement)parent;
    }
    if (couldContainReference(position)) {
      return GroovyPsiElementFactory.getInstance(position.getProject()).createReferenceElementFromText("Foo", position);
    }
    return null;
  }

  private static boolean couldContainReference(PsiElement position) {
    return IN_CATCH_TYPE.accepts(position) || AFTER_AT.accepts(position) || GroovyCompletionUtil.isFirstElementAfterPossibleModifiersInVariableDeclaration(
      position,
      true) || GroovyCompletionUtil
      .isTupleVarNameWithoutTypeDeclared(position);
  }

  @Nullable
  private static GrTypeParameterList findTypeParameterListCandidate(@Nonnull PsiElement position) {
    final PsiElement parent = position.getParent();
    if (parent instanceof GrVariable) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof GrVariableDeclaration) {
        final PsiElement errorElement = PsiUtil.skipWhitespacesAndComments(parent.getPrevSibling(), false);
        if (errorElement instanceof PsiErrorElement) {
          final PsiElement child = errorElement.getFirstChild();
          if (child instanceof GrTypeParameterList) {
            return (GrTypeParameterList)child;
          }
        }
      }
    }
    return null;
  }

  public static boolean isClassNamePossible(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceElement) {
      return ((GrReferenceElement)parent).getQualifier() == null;
    }
    return couldContainReference(position);
  }

  private static void addAllClasses(CompletionParameters parameters,
                                    final CompletionResultSet result,
                                    final JavaCompletionSession inheritors) {
    addAllClasses(parameters, new Consumer<LookupElement>() {
      @Override
      public void accept(LookupElement element) {
        result.addElement(element);
      }
    }, inheritors, result.getPrefixMatcher());
  }

  public static void addAllClasses(CompletionParameters parameters,
                                   final Consumer<LookupElement> consumer,
                                   final JavaCompletionSession inheritors,
                                   final PrefixMatcher matcher) {
    final PsiElement position = parameters.getPosition();
    final boolean afterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    AllClassesGetter.processJavaClasses(parameters, matcher, parameters.getInvocationCount() <= 1, new Consumer<PsiClass>() {
      @Override
      public void accept(PsiClass psiClass) {
        for (JavaPsiClassReferenceElement element : JavaClassNameCompletionContributor.createClassLookupItems(psiClass,
                                                                                                              afterNew,
                                                                                                              new GroovyClassNameInsertHandler(),
                                                                                                              new Condition<PsiClass>() {
                                                                                                                @Override
                                                                                                                public boolean value(
                                                                                                                  PsiClass psiClass) {
                                                                                                                  return !inheritors.alreadyProcessed(
                                                                                                                    psiClass);
                                                                                                                }
                                                                                                              })) {
          consumer.accept(element);
        }
      }
    });
  }

  @Nonnull
  static Runnable completeReference(final CompletionParameters parameters,
                                    final GrReferenceElement reference,
                                    final JavaCompletionSession inheritorsHolder,
                                    final PrefixMatcher matcher,
                                    final Consumer<LookupElement> _consumer) {
    final Consumer<LookupElement> consumer = new Consumer<LookupElement>() {
      final Set<LookupElement> added = new HashSet<>();

      @Override
      public void accept(LookupElement element) {
        if (added.add(element)) {
          _consumer.accept(element);
        }
      }
    };

    final Map<PsiModifierListOwner, LookupElement> staticMembers = new HashMap<>();
    final PsiElement qualifier = reference.getQualifier();
    final PsiType qualifierType = qualifier instanceof GrExpression ? ((GrExpression)qualifier).getType() : null;

    if (reference instanceof GrReferenceExpression && (qualifier instanceof GrExpression || qualifier == null)) {
      for (String string : CompleteReferencesWithSameQualifier.getVariantsWithSameQualifier((GrReferenceExpression)reference,
                                                                                            matcher,
                                                                                            (GrExpression)qualifier)) {
        consumer.accept(LookupElementBuilder.create(string).withItemTextUnderlined(true));
      }
      if (parameters.getInvocationCount() < 2 && qualifier != null && qualifierType == null && !(qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier)
        .resolve()
        instanceof PsiJavaPackage)) {
        if (parameters.getInvocationCount() == 1) {
          showInfo();
        }
        return EmptyRunnable.INSTANCE;
      }
    }

    final List<LookupElement> zeroPriority = ContainerUtil.newArrayList();

    GroovyCompletionUtil.processVariants(reference, matcher, parameters, new Consumer<LookupElement>() {
      @Override
      public void accept(LookupElement lookupElement) {
        Object object = lookupElement.getObject();
        if (object instanceof GroovyResolveResult) {
          object = ((GroovyResolveResult)object).getElement();
        }

        if (isLightElementDeclaredDuringCompletion(object)) {
          return;
        }

        if (!(lookupElement instanceof LookupElementBuilder) && inheritorsHolder.alreadyProcessed(lookupElement)) {
          return;
        }

        if (object instanceof PsiMember && JavaCompletionUtil.isInExcludedPackage((PsiMember)object, true)) {
          return;
        }

        if (!(object instanceof PsiClass)) {
          int priority = assignPriority(lookupElement, qualifierType);
          lookupElement = JavaCompletionUtil.highlightIfNeeded(qualifierType,
																															 PrioritizedLookupElement.withPriority(lookupElement, priority),
																															 object,
																															 reference);
        }

        if ((object instanceof PsiMethod || object instanceof PsiField) && ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
          if (lookupElement.getLookupString().equals(((PsiMember)object).getName())) {
            staticMembers.put(CompletionUtilCore.getOriginalOrSelf((PsiModifierListOwner)object), lookupElement);
          }
        }

        PrioritizedLookupElement prio = lookupElement.as(PrioritizedLookupElement.CLASS_CONDITION_KEY);
        if (prio == null || prio.getPriority() == 0) {
          zeroPriority.add(lookupElement);
        }
        else {
          consumer.accept(lookupElement);
        }
      }
    });

    for (LookupElement element : zeroPriority) {
      consumer.accept(element);
    }

    if (qualifier == null) {
      return addStaticMembers(parameters, matcher, staticMembers, consumer);
    }
    return EmptyRunnable.INSTANCE;
  }

  private static boolean isLightElementDeclaredDuringCompletion(Object object) {
    if (!(object instanceof LightElement && object instanceof PsiNamedElement)) {
      return false;
    }
    final String name = ((PsiNamedElement)object).getName();
    if (name == null) {
      return false;
    }

    return name.contains(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED.trim()) || name.contains(GrDummyIdentifierProvider.DUMMY_IDENTIFIER_DECAPITALIZED
                                                                                                             .trim());
  }

  private static Runnable addStaticMembers(CompletionParameters parameters,
                                           final PrefixMatcher matcher,
                                           final Map<PsiModifierListOwner, LookupElement> staticMembers,
                                           final Consumer<LookupElement> consumer) {
    final StaticMemberProcessor processor = completeStaticMembers(parameters);
    processor.processMembersOfRegisteredClasses(matcher, new PairConsumer<PsiMember, PsiClass>() {
      @Override
      public void consume(PsiMember member, PsiClass psiClass) {
        if (member instanceof GrAccessorMethod) {
          member = ((GrAccessorMethod)member).getProperty();
        }
        member = CompletionUtilCore.getOriginalOrSelf(member);
        if (staticMembers.containsKey(member)) {
          return;
        }
        final String name = member.getName();
        if (name == null || !matcher.prefixMatches(name)) {
          staticMembers.remove(member);
          return;
        }
        JavaGlobalMemberLookupElement element = createGlobalMemberElement(member, psiClass, true);
        staticMembers.put(member, element);
        consumer.accept(element);
      }
    });
    if (parameters.getInvocationCount() >= 2 && StringUtil.isNotEmpty(matcher.getPrefix())) {
      return new Runnable() {
        @Override
        public void run() {
          processor.processStaticMethodsGlobally(matcher, new Consumer<LookupElement>() {
            @Override
            public void accept(LookupElement element) {
              PsiMember member = (PsiMember)element.getObject();
              if (member instanceof GrAccessorMethod) {
                member = ((GrAccessorMethod)member).getProperty();
              }
              member = CompletionUtilCore.getOriginalOrSelf(member);
              if (staticMembers.containsKey(member)) {
                return;
              }
              staticMembers.put(member, element);
              consumer.accept(element);
            }
          });
        }
      };
    }
    return EmptyRunnable.INSTANCE;
  }

  private static void showInfo() {
    CompletionService.getCompletionService()
										 .setAdvertisementText(GroovyBundle.message("invoke.completion.second.time.to.show.skipped.methods"));
  }

  private static boolean checkForIterator(PsiMethod method) {
    if (!"next".equals(method.getName())) {
      return false;
    }

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final PsiClass iterator =
      JavaPsiFacade.getInstance(method.getProject()).findClass(CommonClassNames.JAVA_UTIL_ITERATOR, method.getResolveScope());
    return InheritanceUtil.isInheritorOrSelf(containingClass, iterator, true);
  }

  private static int assignPriority(LookupElement lookupElement, PsiType qualifierType) {
    Object object = lookupElement.getObject();
    PsiSubstitutor substitutor = null;
    GroovyResolveResult resolveResult = null;
    if (object instanceof GroovyResolveResult) {
      resolveResult = (GroovyResolveResult)object;
      substitutor = resolveResult.getSubstitutor();
      object = ((GroovyResolveResult)object).getElement();
    }

    // default groovy methods
    if (object instanceof GrGdkMethod && GroovyCompletionUtil.skipDefGroovyMethod((GrGdkMethod)object, substitutor, qualifierType)) {
      return -1;
    }

    // operator methods
    if (object instanceof PsiMethod && PsiUtil.OPERATOR_METHOD_NAMES.contains(((PsiMethod)object).getName()) && !checkForIterator((PsiMethod)object)) {
      return -3;
    }

    // accessors if there is no get, set, is prefix
    if (object instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)object)) {
      return -1;
    }

    // inaccessible elements
    if (resolveResult != null && !resolveResult.isAccessible()) {
      return -2;
    }
    return 0;
  }

  static StaticMemberProcessor completeStaticMembers(CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    final PsiElement originalPosition = parameters.getOriginalPosition();
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @Nonnull
      @Override
      protected LookupElement createLookupElement(@Nonnull PsiMember member, @Nonnull PsiClass containingClass, boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        return createGlobalMemberElement(member, containingClass, shouldImport);
      }

      @Override
      protected LookupElement createLookupElement(@Nonnull List<PsiMethod> overloads,
                                                  @Nonnull PsiClass containingClass,
                                                  boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        return new JavaGlobalMemberLookupElement(overloads,
                                                 containingClass,
                                                 QualifiedMethodInsertHandler.INSTANCE,
                                                 StaticImportInsertHandler.INSTANCE,
                                                 shouldImport);
      }

      @Override
      protected boolean isAccessible(PsiMember member) {
        boolean result = super.isAccessible(member);

        if (!result && member instanceof GrField) {
          GrAccessorMethod[] getters = ((GrField)member).getGetters();
          return getters.length > 0 && super.isAccessible(getters[0]);
        }

        return result;
      }
    };
    final PsiFile file = position.getContainingFile();
    if (file instanceof GroovyFile) {
      for (GrImportStatement statement : ((GroovyFile)file).getImportStatements()) {
        if (statement.isStatic()) {
          GrCodeReferenceElement importReference = statement.getImportReference();
          if (importReference != null) {
            if (!statement.isOnDemand()) {
              importReference = importReference.getQualifier();
            }
            if (importReference != null) {
              final PsiElement target = importReference.resolve();
              if (target instanceof PsiClass) {
                processor.importMembersOf((PsiClass)target);
              }
            }
          }
        }
      }
    }
    return processor;
  }

  static JavaGlobalMemberLookupElement createGlobalMemberElement(PsiMember member, PsiClass containingClass, boolean shouldImport) {
    return new JavaGlobalMemberLookupElement(member,
                                             containingClass,
                                             QualifiedMethodInsertHandler.INSTANCE,
                                             StaticImportInsertHandler.INSTANCE,
                                             shouldImport);
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement(PsiElement.class), new GrMainCompletionProvider());
  }

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters,
                             ProcessingContext context,
                             @Nonnull final CompletionResultSet result) {
    GroovyCompletionData.addGroovyDocKeywords(parameters, result);

    PsiElement position = parameters.getPosition();
    if (PlatformPatterns.psiElement().inside(false, PlatformPatterns.psiElement(PsiComment.class)).accepts(position)) {
      return;
    }

    GroovyCompletionData.addGroovyKeywords(parameters, result);

    addUnfinishedMethodTypeParameters(position, result);

    suggestVariableNames(position, result);

    GrReferenceElement reference = findGroovyReference(position);
    if (reference == null) {
      if (parameters.getInvocationCount() >= 2) {
        result.stopHere();
        addAllClasses(parameters,
                      result.withPrefixMatcher(CompletionUtilCore.findJavaIdentifierPrefix(parameters)),
                      new JavaCompletionSession(result));
      }
      return;
    }

    if (reference.getParent() instanceof GrImportStatement && reference.getQualifier() != null) {
      result.addElement(LookupElementBuilder.create("*"));
    }

    JavaCompletionSession inheritors = new JavaCompletionSession(result);
    if (GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
      GroovySmartCompletionContributor.generateInheritorVariants(parameters, result.getPrefixMatcher(), inheritors::addClassItem);
    }

    Runnable addSlowVariants =
      completeReference(parameters, reference, inheritors, result.getPrefixMatcher(), lookupElement -> result.addElement(lookupElement));

    if (reference.getQualifier() == null) {
      if (!GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
        GroovySmartCompletionContributor.addExpectedClassMembers(parameters, result);
      }

      if (isClassNamePossible(position) && JavaCompletionContributor.mayStartClassName(result)) {
        result.stopHere();
        if (parameters.getInvocationCount() >= 2) {
          addAllClasses(parameters, result, inheritors);
        }
        else {
          JavaCompletionContributor.advertiseSecondCompletion(position.getProject(), result);
        }
      }
    }

    addSlowVariants.run();
  }
}
