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

import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.codeInsight.completion.JavaInheritorsGetter;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.language.impl.psi.PsiDiamondTypeImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.Document;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Sets;
import consulo.util.collection.SmartList;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.impl.lang.completion.handlers.AfterNewClassInsertHandler;
import org.jetbrains.plugins.groovy.impl.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovySmartCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> INSIDE_EXPRESSION = PlatformPatterns.psiElement().withParent(GrExpression.class);
  private static final ElementPattern<PsiElement> IN_CAST_PARENTHESES =
    PlatformPatterns.psiElement().withSuperParent(3, PlatformPatterns.psiElement(GrTypeCastExpression.class).withParent
      (StandardPatterns.or(PlatformPatterns.psiElement(GrAssignmentExpression.class), PlatformPatterns.psiElement(GrVariable.class))));

  static final ElementPattern<PsiElement> AFTER_NEW =
    PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(GroovyTokenTypes.kNEW));

  private static final ElementPattern<PsiElement> IN_ANNOTATION =
    PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class).withParent
      (GrAnnotationNameValuePair.class));

  private static final HashingStrategy<TypeConstraint> EXPECTED_TYPE_INFO_STRATEGY = new HashingStrategy<TypeConstraint>() {
    @Override
    public int hashCode(final TypeConstraint object) {
      return object.getType().hashCode();
    }

    @Override
    public boolean equals(final TypeConstraint o1, final TypeConstraint o2) {
      return o1.getClass().equals(o2.getClass()) && o1.getType().equals(o2.getType());
    }
  };

  public GroovySmartCompletionContributor() {
    extend(CompletionType.SMART, INSIDE_EXPRESSION, new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull final CompletionParameters params,
                                 ProcessingContext context,
                                 @Nonnull final CompletionResultSet result) {
        final PsiElement position = params.getPosition();
        if (position.getParent() instanceof GrLiteral) {
          return;
        }

        if (isInDefaultAnnotationNameValuePair(position)) {
          return;
        }

        final Set<TypeConstraint> infos = getExpectedTypeInfos(params);

        final PsiElement reference = position.getParent();
        if (reference == null) {
          return;
        }
        if (reference instanceof GrReferenceElement) {
          GroovyCompletionUtil.processVariants((GrReferenceElement)reference,
                                               result.getPrefixMatcher(),
                                               params, new Consumer<LookupElement>() {
              @Override
              public void accept(LookupElement variant) {
                PsiType type = null;

                Object o = variant.getObject();
                if (o instanceof GroovyResolveResult) {
                  if (!((GroovyResolveResult)o).isAccessible()) {
                    return;
                  }
                  o = ((GroovyResolveResult)o).getElement();
                }

                if (o instanceof PsiElement) {
                  type = getTypeByElement((PsiElement)o, position);
                }
                else if (o instanceof String) {
                  if ("true".equals(o) || "false".equals(o)) {
                    type = PsiType.BOOLEAN;
                  }
                }
                if (type == null) {
                  return;
                }
                for (TypeConstraint info : infos) {
                  if (info.satisfied(type, position)) {
                    result.addElement(variant);
                    break;
                  }
                }
              }
            });
        }

        addExpectedClassMembers(params, result);
      }
    });

    extend(CompletionType.SMART, IN_CAST_PARENTHESES, new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final GrTypeCastExpression parenthesizedExpression = ((GrTypeCastExpression)position.getParent().getParent().getParent());
        final PsiElement assignment = parenthesizedExpression.getParent();
        if (assignment instanceof GrAssignmentExpression && ((GrAssignmentExpression)assignment).getLValue() == parenthesizedExpression) {
          return;
        }

        final boolean overwrite = PlatformPatterns.psiElement()
                                                  .afterLeaf(PlatformPatterns.psiElement()
                                                                             .withText("(")
                                                                             .withParent(GrTypeCastExpression.class))
                                                  .accepts(parameters
                                                             .getOriginalPosition());
        final Set<TypeConstraint> typeConstraints = getExpectedTypeInfos(parameters);
        for (TypeConstraint typeConstraint : typeConstraints) {
          final PsiType type = typeConstraint.getType();
          final PsiTypeLookupItem
            item = PsiTypeLookupItem.createLookupItem(type, position, PsiTypeLookupItem.isDiamond(type), ChooseTypeExpression.IMPORT_FIXER)
                                    .setShowPackage();
          item.setInsertHandler(new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");

              final Editor editor = context.getEditor();
              final Document document = editor.getDocument();
              if (overwrite) {
                document.deleteString(context.getSelectionEndOffset(),
                                      context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
              }

              final CodeStyleSettings csSettings = CodeStyleSettingsManager.getSettings(context.getProject());
              final int oldTail = context.getTailOffset();
              context.setTailOffset(GroovyCompletionUtil.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

              if (csSettings.SPACE_AFTER_TYPE_CAST) {
                context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
              }

              editor.getCaretModel().moveToOffset(context.getTailOffset());
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
            }
          });
          result.addElement(item);
        }
      }
    });

    extend(CompletionType.SMART, AFTER_NEW, new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull final CompletionParameters parameters,
                                 final ProcessingContext matchingContext,
                                 @Nonnull final CompletionResultSet result) {
        generateInheritorVariants(parameters, result.getPrefixMatcher(), lookupElement -> result.addElement(lookupElement));
      }
    });

    extend(CompletionType.SMART, IN_ANNOTATION, new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull CompletionParameters params,
                                 ProcessingContext context,
                                 @Nonnull final CompletionResultSet result) {
        final PsiElement position = params.getPosition();

        if (!isInDefaultAnnotationNameValuePair(position)) {
          return;
        }

        final GrReferenceExpression reference = (GrReferenceExpression)position.getParent();
        if (reference == null) {
          return;
        }

        CompleteReferenceExpression.processRefInAnnotation(reference, result.getPrefixMatcher(), new Consumer<LookupElement>() {
          @Override
          public void accept(@Nullable LookupElement element) {
            if (element != null) {
              result.addElement(element);
            }
          }
        }, params);
      }
    });

  }

  /**
   * we are here: @Abc(<caret>)
   * where Abc does not have 'value' attribute
   */
  private static boolean isInDefaultAnnotationNameValuePair(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceExpression) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrAnnotationNameValuePair) {
        PsiElement identifier = ((GrAnnotationNameValuePair)pparent).getNameIdentifierGroovy();
        if (identifier == null) {
          PsiElement ppparent = pparent.getParent().getParent();
          if (ppparent instanceof GrAnnotation) {
            PsiElement resolved = ((GrAnnotation)ppparent).getClassReference().resolve();
            if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
              PsiMethod[] values = ((PsiClass)resolved).findMethodsByName("value", false);
              return values.length == 0;
            }
          }
        }
      }
    }

    return false;
  }

  static void addExpectedClassMembers(CompletionParameters params, final CompletionResultSet result) {
    for (final TypeConstraint info : getExpectedTypeInfos(params)) {
      Consumer<LookupElement> consumer = element -> result.addElement(element);
      PsiType type = info.getType();
      PsiType defType = info.getDefaultType();
      boolean searchInheritors = params.getInvocationCount() > 1;
      if (type instanceof PsiClassType) {
        new GroovyMembersGetter((PsiClassType)type, params).processMembers(searchInheritors, consumer);
      }
      if (!defType.equals(type) && defType instanceof PsiClassType) {
        new GroovyMembersGetter((PsiClassType)defType, params).processMembers(searchInheritors, consumer);
      }
    }
  }

  static void generateInheritorVariants(final CompletionParameters parameters,
                                        PrefixMatcher matcher,
                                        final Consumer<LookupElement> consumer) {
    final PsiElement place = parameters.getPosition();
    final GrExpression expression = PsiTreeUtil.getParentOfType(place, GrExpression.class);
    if (expression == null) {
      return;
    }

    GrExpression placeToInferType = expression;
    if (expression.getParent() instanceof GrApplicationStatement && expression.getParent().getParent() instanceof GrAssignmentExpression) {
      placeToInferType = (GrExpression)expression.getParent();
    }

    for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(placeToInferType)) {
      if (type instanceof PsiArrayType) {
        final PsiType _type = GenericsUtil.eliminateWildcards(type);
        final PsiTypeLookupItem item =
          PsiTypeLookupItem.createLookupItem(_type, place, PsiTypeLookupItem.isDiamond(_type), ChooseTypeExpression.IMPORT_FIXER)
                           .setShowPackage();
        if (item.getObject() instanceof PsiClass) {
          item.setInsertHandler(new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
            }
          });
        }
        consumer.accept(item);
      }
    }


    final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();

    for (PsiType psiType : GroovyExpectedTypesProvider.getDefaultExpectedTypes(placeToInferType)) {
      if (psiType instanceof PsiClassType) {
        PsiType type = GenericsUtil.eliminateWildcards(JavaCompletionUtil.originalize(psiType));
        final PsiClassType classType = (PsiClassType)type;
        if (classType.resolve() != null) {
          expectedClassTypes.add(classType);
        }
      }
    }

    final PsiType diamond = inferDiamond(place);

    JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, matcher, type -> {
      final LookupElement element = addExpectedType(type, place, parameters, diamond);
      if (element != null) {
        consumer.accept(element);
      }
    });
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  @Nullable
  private static PsiType inferDiamond(PsiElement place) {
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(place, GroovyConfigUtils.GROOVY1_8)) {
      return null;
    }

    final PsiElement parent = place.getParent().getParent();
    if (!(parent instanceof GrNewExpression)) {
      return null;
    }

    final PsiElement pparent = parent.getParent();

    if (pparent instanceof GrVariable) {
      return ((GrVariable)pparent).getDeclaredType();
    }
    else if (pparent instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignment = (GrAssignmentExpression)pparent;
      IElementType optoken = assignment.getOperationTokenType();

      GrExpression lvalue = assignment.getLValue();
      GrExpression rvalue = assignment.getRValue();

      if (parent == rvalue && optoken == GroovyTokenTypes.mASSIGN) {
        return lvalue.getNominalType();
      }
    }
    else if (pparent instanceof GrApplicationStatement) {
      PsiElement ppparent = pparent.getParent();
      if (ppparent instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression)ppparent;
        IElementType optoken = assignment.getOperationTokenType();

        GrExpression lvalue = assignment.getLValue();
        GrExpression rvalue = assignment.getRValue();

        if (pparent == rvalue && optoken == GroovyTokenTypes.mASSIGN) {
          return lvalue.getNominalType();
        }
      }
    }
    return null;
  }

  @Override
  public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
  }

  @Nullable
  private static LookupElement addExpectedType(PsiType type,
                                               final PsiElement place,
                                               CompletionParameters parameters,
                                               @Nullable PsiType diamond) {
    if (!JavaCompletionUtil.hasAccessibleConstructor(type, place)) {
      return null;
    }

    final PsiClass psiClass = com.intellij.java.language.psi.util.PsiUtil.resolveClassInType(type);
    if (psiClass == null) {
      return null;
    }

    if (!checkForInnerClass(psiClass, place)) {
      return null;
    }


    boolean isDiamond = false;
    if (diamond != null &&
      psiClass.hasTypeParameters() &&
      !((PsiClassType)type).isRaw() &&
      !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final String canonicalText = TypeConversionUtil.erasure(type).getCanonicalText();
      final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(place.getProject());
      final String text = diamond.getCanonicalText() + " v = new " + canonicalText + "<>()";
      final GrStatement statement = elementFactory.createStatementFromText(text, parameters.getOriginalFile());
      final GrVariable declaredVar = ((GrVariableDeclaration)statement).getVariables()[0];
      final GrNewExpression initializer = (GrNewExpression)declaredVar.getInitializerGroovy();
      assert initializer != null;
      final boolean hasDefaultConstructorOrNoGenericsOne =
        PsiDiamondTypeImpl.hasDefaultConstructor(psiClass) || !PsiDiamondTypeImpl.haveConstructorsGenericsParameters(psiClass);
      final PsiType initializerType = initializer.getType();
      if (hasDefaultConstructorOrNoGenericsOne &&
        initializerType != null &&
        initializerType instanceof PsiClassType &&
        ((PsiClassType)initializerType).getParameters().length > 0) {
        type = initializerType;
        isDiamond = true;
      }
    }

    final PsiTypeLookupItem item =
      PsiTypeLookupItem.createLookupItem(GenericsUtil.eliminateWildcards(type), place, isDiamond, ChooseTypeExpression.IMPORT_FIXER)
                       .setShowPackage();
    Object object = item.getObject();
    if (object instanceof PsiClass && ((PsiClass)object).hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setIndicateAnonymous(true);
    }
    item.setInsertHandler(new AfterNewClassInsertHandler((PsiClassType)type, true));
    return item;
  }

  private static boolean checkForInnerClass(PsiClass psiClass, PsiElement identifierCopy) {
    return !com.intellij.java.language.psi.util.PsiUtil.isInnerClass(psiClass) || PsiUtil.hasEnclosingInstanceInScope(psiClass.getContainingClass(),
                                                                                                                      identifierCopy,
                                                                                                                      true);
  }


  @Override
  public void beforeCompletion(@Nonnull CompletionInitializationContext context) {
    if (context.getCompletionType() != CompletionType.SMART) {
      return;
    }

    PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
    if (lastElement != null && lastElement.getText().equals("(")) {
      final PsiElement parent = lastElement.getParent();
      if (parent instanceof GrTypeCastExpression) {
        context.setDummyIdentifier("");
      }
      else if (parent instanceof GrParenthesizedExpression) {
        context.setDummyIdentifier("xxx)yyy "); // to handle type cast
      }
    }
  }

  private static Set<TypeConstraint> getExpectedTypeInfos(final CompletionParameters params) {
    return Sets.newHashSet(Arrays.asList(getExpectedTypes(params)), EXPECTED_TYPE_INFO_STRATEGY);
  }

  @Nonnull
  public static TypeConstraint[] getExpectedTypes(CompletionParameters params) {
    final PsiElement position = params.getPosition();
    final GrExpression expression = PsiTreeUtil.getParentOfType(position, GrExpression.class);
    if (expression != null) {
      return GroovyExpectedTypesProvider.calculateTypeConstraints(expression);
    }
    return TypeConstraint.EMPTY_ARRAY;
  }

  @Nullable
  public static PsiType getTypeByElement(PsiElement element, PsiElement context) {
    //if(!element.isValid()) return null;
    if (element instanceof PsiType) {
      return (PsiType)element;
    }
    if (element instanceof PsiClass) {
      return PsiType.getJavaLangClass(context.getManager(), GlobalSearchScope.allScope(context.getProject()));
    }
    if (element instanceof PsiMethod) {
      return PsiUtil.getSmartReturnType((PsiMethod)element);
    }
    if (element instanceof GrVariable) {
      if (PsiUtil.isLocalVariable(element)) {
        return TypeInferenceHelper.getInferredType(context, ((GrVariable)element).getName());
      }
      else {
        return ((GrVariable)element).getTypeGroovy();
      }
    }
/*    if(element instanceof PsiKeyword){
    return getKeywordItemType(context, element.getText());
    }*/
    if (element instanceof GrExpression) {
      return ((GrExpression)element).getType();
    }
    if (element instanceof PsiField) {
      return ((PsiField)element).getType();
    }

    return null;
  }


}
