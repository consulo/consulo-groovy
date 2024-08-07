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

import com.intellij.java.impl.codeInsight.completion.AllClassesGetter;
import com.intellij.java.impl.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.progress.ProgressManager;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.HighlighterIterator;
import consulo.component.util.Iconable;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.*;
import consulo.language.psi.filter.FilterPositionUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import consulo.util.lang.function.Conditions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author ilyas
 */
public class GroovyCompletionUtil {

  private GroovyCompletionUtil() {
  }

  /**
   * Return true if last element of current statement is expression
   *
   * @param statement
   * @return
   */
  public static boolean endsWithExpression(PsiElement statement) {
    while (statement != null &&
           !(statement instanceof GrExpression)) {
      statement = statement.getLastChild();
      if (statement instanceof PsiErrorElement) {
        statement = nearestLeftSibling(statement);
      }
    }
    return statement != null;
  }

  @Nullable
  public static PsiElement nearestLeftSibling(PsiElement elem) {
    elem = elem.getPrevSibling();
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            GroovyTokenTypes.mNLS.equals(elem.getNode().getElementType()))) {
      elem = elem.getPrevSibling();
    }
    return elem;
  }

  @Nullable
  public static PsiElement nearestLeftLeaf(PsiElement elem) {
    elem = PsiTreeUtil.prevLeaf(elem);
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            GroovyTokenTypes.mNLS.equals(elem.getNode().getElementType()))) {
      elem = PsiTreeUtil.prevLeaf(elem);
    }
    return elem;
  }

  /**
   * Shows whether keyword may be placed as a new statement beginning
   *
   * @param element
   * @param canBeAfterBrace May be after '{' symbol or not
   * @return
   */
  public static boolean isNewStatement(PsiElement element, boolean canBeAfterBrace) {
    PsiElement previousLeaf = getLeafByOffset(element.getTextRange().getStartOffset() - 1, element);
    previousLeaf = PsiImplUtil.realPrevious(previousLeaf);
    if (previousLeaf != null) {
      if (canBeAfterBrace && GroovyTokenTypes.mLCURLY.equals(previousLeaf.getNode().getElementType())) {
        return true;
      }
      if (GroovyTokenTypes.mCOLON.equals(previousLeaf.getNode().getElementType()) && previousLeaf.getParent() instanceof GrLabeledStatement) {
        return true;
      }
    }
    return (previousLeaf == null || SEPARATORS.contains(previousLeaf.getNode().getElementType()));
  }

  @Nullable
  public static PsiElement getLeafByOffset(int offset, PsiElement element) {
    if (offset < 0) {
      return null;
    }
    PsiElement candidate = element.getContainingFile();
    while (candidate.getNode().getFirstChildNode() != null) {
      candidate = candidate.findElementAt(offset);
    }
    return candidate;
  }

  /**
   * return true, if the element is first element after modifiers and there is no type element
   */
  public static boolean isFirstElementAfterPossibleModifiersInVariableDeclaration(PsiElement element, boolean acceptParameter) {
    if (element.getParent() instanceof GrTypeDefinitionBody && !(element instanceof PsiComment)) {
      //is first on the line?
      String text = element.getContainingFile().getText();
      int i = CharArrayUtil.shiftBackward(text, element.getTextRange().getStartOffset() - 1, " \t");
      return i >= 0 && (text.charAt(i) == '\n' || text.charAt(i) == '{');
    }

    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrVariable)) return false;

    if (acceptParameter && parent instanceof GrParameter) {
      return ((GrParameter)parent).getTypeElementGroovy() == null;
    }

    final PsiElement pparent = parent.getParent();
    if (!(pparent instanceof GrVariableDeclaration)) return false;
    if (((GrVariableDeclaration)pparent).isTuple()) return false;

    final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)pparent;
    if (variableDeclaration.getTypeElementGroovy() != null) return false;

    return variableDeclaration.getVariables()[0] == parent;
  }

  private static final TokenSet SEPARATORS = TokenSet.create(GroovyTokenTypes.mNLS,
                                                             GroovyTokenTypes.mSEMI);

  public static boolean asSimpleVariable(PsiElement context) {
    return isInTypeDefinitionBody(context) &&
           isNewStatement(context, true);
  }

  public static boolean isInTypeDefinitionBody(PsiElement context) {
    return (context.getParent() instanceof GrCodeReferenceElement &&
            context.getParent().getParent() instanceof GrClassTypeElement &&
            context.getParent().getParent().getParent() instanceof GrTypeDefinitionBody) ||
           context.getParent() instanceof GrTypeDefinitionBody;
  }

  public static boolean asVariableInBlock(PsiElement context) {
    if (context.getParent() instanceof GrReferenceExpression) {
      PsiElement parent = context.getParent().getParent();
      while (parent instanceof GrStatement) {
        parent = parent.getParent();
      }
      if ((parent instanceof GrControlFlowOwner || parent instanceof GrCaseSection) && isNewStatement(context, true)) {
        return true;
      }
    }

    return context.getParent() instanceof GrTypeDefinitionBody && isNewStatement(context, true);
  }

  public static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement &&
           context.getParent().getParent() instanceof GrTypeElement &&
           context.getParent().getParent().getParent() instanceof GrMethod &&
           context.getParent().getParent().getParent().getParent() instanceof GrTypeDefinitionBody &&
           context.getTextRange().getStartOffset() ==
           context.getParent().getParent().getParent().getParent().getTextRange().getStartOffset();
  }


  public static List<LookupElement> getCompletionVariants(GroovyResolveResult[] candidates,
                                                          boolean afterNew,
                                                          PrefixMatcher matcher,
                                                          PsiElement position) {
    List<LookupElement> result = ContainerUtil.newArrayList();
    for (GroovyResolveResult candidate : candidates) {
      result.addAll(createLookupElements(candidate, afterNew, matcher, position));
      ProgressManager.checkCanceled();
    }

    return result;
  }

  public static List<LookupElement> getCompletionVariants(List<GroovyResolveResult> candidates,
                                                          boolean afterNew,
                                                          PrefixMatcher matcher,
                                                          PsiElement position) {
    List<LookupElement> result = ContainerUtil.newArrayList();
    for (GroovyResolveResult candidate : candidates) {
      result.addAll(createLookupElements(candidate, afterNew, matcher, position));
      ProgressManager.checkCanceled();
    }

    return result;
  }


  public static List<? extends LookupElement> createLookupElements(@Nonnull GroovyResolveResult candidate,
                                                                   boolean afterNew,
                                                                   @Nonnull PrefixMatcher matcher,
                                                                   @Nullable PsiElement position) {
    final PsiElement element = candidate.getElement();
    final PsiElement context = candidate.getCurrentFileResolveContext();
    if (context instanceof GrImportStatement && element != null) {
      if (element instanceof PsiPackage) {
        return Collections.emptyList();
      }

      final String importedName = ((GrImportStatement)context).getImportedName();
      if (importedName != null) {
        if (!(matcher.prefixMatches(importedName) ||
              element instanceof PsiMethod && getterMatches(matcher, (PsiMethod)element, importedName) ||
              element instanceof PsiMethod && setterMatches(matcher, (PsiMethod)element, importedName))
          ) {
          return Collections.emptyList();
        }

        final GrCodeReferenceElement importReference = ((GrImportStatement)context).getImportReference();
        if (importReference != null) {
          boolean alias = ((GrImportStatement)context).isAliasedImport();
          for (GroovyResolveResult r : importReference.multiResolve(false)) {
            final PsiElement resolved = r.getElement();
            if (context.getManager().areElementsEquivalent(resolved, element) && (alias || !(element instanceof PsiClass))) {
              return generateLookupForImportedElement(candidate, importedName);
            }
            else {
              if (resolved instanceof PsiField && element instanceof PsiMethod && GroovyPropertyUtils
                .isAccessorFor((PsiMethod)element, (PsiField)resolved)) {
                return generateLookupForImportedElement(candidate, GroovyPropertyUtils.getAccessorPrefix((PsiMethod)element) + GroovyPropertyUtils
                  .capitalize(importedName));
              }
            }
          }
        }
      }
    }

    String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() :   element.getText();
    if (name == null || !matcher.prefixMatches(name)) {
      return Collections.emptyList();
    }

    if (element instanceof PsiClass) {
      return JavaClassNameCompletionContributor
        .createClassLookupItems((PsiClass)element, afterNew, new GroovyClassNameInsertHandler(), Conditions.<PsiClass>alwaysTrue());
    }

    LookupElementBuilder builder = LookupElementBuilder.create(element instanceof PsiPackage ? element : candidate, name);
    return Arrays.asList(setupLookupBuilder(element, candidate.getSubstitutor(), builder, position));
  }

  private static boolean setterMatches(PrefixMatcher matcher, PsiMethod element, String importedName) {
    return GroovyPropertyUtils.isSimplePropertySetter(element) && matcher.prefixMatches(GroovyPropertyUtils.getSetterName(importedName));
  }

  private static boolean getterMatches(PrefixMatcher matcher, PsiMethod element, String importedName) {
    return GroovyPropertyUtils.isSimplePropertyGetter(element) &&
           (matcher.prefixMatches(GroovyPropertyUtils.getGetterNameNonBoolean(importedName)) ||
            element.getReturnType() == PsiType.BOOLEAN && matcher.prefixMatches(GroovyPropertyUtils.getGetterNameBoolean(importedName)));
  }

  public static LookupElement createClassLookupItem(PsiClass psiClass) {
    assert psiClass.isValid();
    return AllClassesGetter.createLookupItem(psiClass, new GroovyClassNameInsertHandler());
  }

  private static List<? extends LookupElement> generateLookupForImportedElement(GroovyResolveResult resolveResult, String importedName) {
    final PsiElement element = resolveResult.getElement();
    assert element != null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    LookupElementBuilder builder = LookupElementBuilder.create(resolveResult, importedName).withPresentableText(importedName);
    return Arrays.asList(setupLookupBuilder(element, substitutor, builder, null));
  }

  public static LookupElement createLookupElement(PsiNamedElement o) {
    return setupLookupBuilder(o, PsiSubstitutor.EMPTY, LookupElementBuilder.create(o, o.getName()), null);
  }

  private static LookupElementBuilder setupLookupBuilder(PsiElement element,
                                                         PsiSubstitutor substitutor,
                                                         LookupElementBuilder builder,
                                                         @Nullable PsiElement position) {
    builder = builder.withIcon(IconDescriptorUpdaters.getIcon(element, Iconable.ICON_FLAG_VISIBILITY | Iconable
			.ICON_FLAG_READ_STATUS))
      .withInsertHandler(GroovyInsertHandler.INSTANCE);
    builder = setTailText(element, builder, substitutor);
    builder = setTypeText(element, builder, substitutor, position);
    return builder;
  }

  private static LookupElementBuilder setTailText(PsiElement element, LookupElementBuilder builder, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiClass aClass = ((PsiMethod)element).getContainingClass();
      if (aClass != null && aClass.isAnnotationType()) {
        return builder;
      }
      builder = builder.withTailText(PsiFormatUtil.formatMethod((PsiMethod)element, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS,
                                                                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
    }
    else if (element instanceof PsiClass) {
      String tailText = getPackageText((PsiClass)element);
      final PsiClass psiClass = (PsiClass)element;
      if ((substitutor == null || substitutor.getSubstitutionMap().isEmpty()) && psiClass.getTypeParameters().length > 0) {
        tailText = "<" + StringUtil.join(psiClass.getTypeParameters(),
                                         psiTypeParameter -> psiTypeParameter.getName(), "," + (showSpaceAfterComma(psiClass) ? " " : "")) + ">" + tailText;
      }
      builder = builder.withTailText(tailText, true);
    }

    return builder;
  }

  private static String getPackageText(PsiClass psiClass) {
    @NonNls String packageName = PsiFormatUtil.getPackageDisplayName(psiClass);
    return " (" + packageName + ")";
  }


  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_AFTER_COMMA;
  }


  private static LookupElementBuilder setTypeText(PsiElement element,
                                                  LookupElementBuilder builder,
                                                  PsiSubstitutor substitutor,
                                                  @Nullable PsiElement position) {
    PsiType type = null;
    if (element instanceof GrVariable) {
      if (position != null && PsiUtil.isLocalVariable(element)) {
        type = TypeInferenceHelper.getInferredType(position, ((GrVariable)element).getName());
      }
      else {
        type = ((GrVariable)element).getTypeGroovy();
      }
    }
    else if (element instanceof PsiVariable) {
      type = ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiMethod) {
      type = substitutor.substitute(((PsiMethod)element).getReturnType());
    }
    return type != null ? builder.withTypeText(type.getPresentableText()) : builder;
  }

  public static boolean hasConstructorParameters(@Nonnull PsiClass clazz, @Nonnull PsiElement place) {
    final GroovyResolveResult[] constructors = ResolveUtil.getAllClassConstructors(clazz, PsiSubstitutor.EMPTY, null, place);


    boolean hasSetters = ContainerUtil.find(clazz.getAllMethods(), new Condition<PsiMethod>() {
      @Override
      public boolean value(PsiMethod method) {
        return GroovyPropertyUtils.isSimplePropertySetter(method);
      }
    }) != null;

    boolean hasParameters = false;
    boolean hasAccessibleConstructors = false;
    for (GroovyResolveResult result : constructors) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiMethod) {
        if (((PsiMethod)element).getParameterList().getParametersCount() > 0) {
          hasParameters = true;
        }
        if (result.isAccessible()) {
          hasAccessibleConstructors = true;
        }
        if (hasAccessibleConstructors && hasParameters) return true;
      }
    }

    return !hasAccessibleConstructors && (hasParameters || hasSetters);
  }

  public static void addImportForItem(PsiFile file, int startOffset, LookupElement item) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    Object o = item.getObject();
    if (o instanceof PsiClass) {
      PsiClass aClass = (PsiClass)o;
      if (aClass.getQualifiedName() == null) return;
      final String lookupString = item.getLookupString();
      int length = lookupString.length();
      final int i = lookupString.indexOf('<');
      if (i >= 0) length = i;
      final int newOffset = addImportForClass(file, startOffset, startOffset + length, aClass);
      shortenReference(file, newOffset);
    }
    else if (o instanceof PsiType) {
      PsiType type = ((PsiType)o).getDeepComponentType();
      if (type instanceof PsiClassType) {
        PsiClass refClass = ((PsiClassType)type).resolve();
        if (refClass != null) {
          int length = refClass.getName().length();
          addImportForClass(file, startOffset, startOffset + length, refClass);
        }
      }
    }
  }

  public static int addImportForClass(PsiFile file, int startOffset, int endOffset, PsiClass aClass) throws IncorrectOperationException
  {
//    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
//    LOG.assertTrue(
//      ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().getCurrentWriteAction(null) != null);

    final PsiManager manager = file.getManager();

    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);

    int newStartOffset = startOffset;

    final PsiReference reference = file.findReferenceAt(endOffset - 1);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if (((PsiClass)resolved).getQualifiedName() == null || manager.areElementsEquivalent(aClass, resolved)) {
          return newStartOffset;
        }
      }
    }

    String name = aClass.getName();
    document.replaceString(startOffset, endOffset, name);

    final RangeMarker toDelete = JavaCompletionUtil.insertTemporary(endOffset, document, " ");

    PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    final PsiReference ref = file.findReferenceAt(startOffset);
    if (ref instanceof GrReferenceElement && aClass.isValid()) {
      PsiElement newElement = ref.bindToElement(aClass);
      RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newElement);
      newStartOffset = marker.getStartOffset();
    }

    if (toDelete != null && toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  //need to shorten references in type argument list
  public static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final Project project = file.getProject();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document document = manager.getDocument(file);
    assert document != null;
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof GrCodeReferenceElement) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences((GroovyPsiElement)ref);
    }
  }

  public static int addRParenth(Editor editor, int oldTail, boolean space_within_cast_parentheses) {
    int offset = -1;

    final HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(oldTail);
    while (!iterator.atEnd()) {
      final IElementType tokenType = (IElementType)iterator.getTokenType();
      if (TokenSets.WHITE_SPACES_OR_COMMENTS.contains(tokenType)) {
        iterator.advance();
        continue;
      }
      if (tokenType == GroovyTokenTypes.mRPAREN) {
        offset = iterator.getEnd();
      }
      break;
    }
    if (offset != -1) return offset;
    offset = oldTail;
    if (space_within_cast_parentheses) {
      offset = TailType.insertChar(editor, oldTail, ' ');
    }
    return TailType.insertChar(editor, offset, ')');
  }

  public static boolean skipDefGroovyMethod(GrGdkMethod gdkMethod, PsiSubstitutor substitutor, @Nullable PsiType type) {
    if (type == null) return false;
    String name = gdkMethod.getStaticMethod().getName();

    final PsiType baseType = gdkMethod.getStaticMethod().getParameterList().getParameters()[0].getType();
    if (!TypeConversionUtil.erasure(baseType).equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return false;

    final PsiType substituted = substitutor != null ? substitutor.substitute(baseType) : baseType;

    if (GdkMethodUtil.COLLECTION_METHOD_NAMES.contains(name)) {
      return !(type instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE) ||
               substituted instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(substituted, CommonClassNames.JAVA_LANG_ITERABLE));
    }
    if (GdkMethodUtil.isWithName(name)) return false;

    return true;
  }

  /*
  we are here:  foo(List<? <caret> ...
   */
  public static boolean isWildcardCompletion(PsiElement position) {
    PsiElement prev = PsiUtil.getPreviousNonWhitespaceToken(position);
    if (prev instanceof PsiErrorElement) prev = PsiUtil.getPreviousNonWhitespaceToken(prev);

    if (prev == null || prev.getNode().getElementType() != GroovyTokenTypes.mQUESTION) return false;

    final PsiElement pprev = PsiUtil.getPreviousNonWhitespaceToken(prev);
    if (pprev == null) return false;

    final IElementType t = pprev.getNode().getElementType();
    return t == GroovyTokenTypes.mLT || t == GroovyTokenTypes.mCOMMA;
  }

  static boolean isNewStatementInScript(PsiElement context) {
    final PsiElement leaf = getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null && isNewStatement(context, false)) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        return true;
      }
    }
    return false;
  }

  public static boolean isReferenceElementInNewExpr(PsiElement context) {
    if (context.getParent() instanceof GrCodeReferenceElement) {
      PsiElement pparent = context.getParent().getParent();
      if (pparent instanceof GrNewExpression) return true;
    }

    return false;
  }

  static boolean isCodeReferenceElementApplicableToModifierCompletion(PsiElement context) {
    return context.getParent() instanceof GrCodeReferenceElement &&
           !(context.getParent().getParent() instanceof GrImportStatement) &&
           !(context.getParent().getParent() instanceof GrPackageDefinition) &&
           !(context.getParent().getParent() instanceof GrNewExpression);
  }

  static boolean isTypelessParameter(PsiElement context) {
    return (context.getParent() instanceof GrParameter && ((GrParameter)context.getParent()).getTypeElementGroovy() == null);
  }

  public static boolean isTupleVarNameWithoutTypeDeclared(PsiElement position) {
    PsiElement parent = position.getParent();
    PsiElement pparent = parent.getParent();
    return parent instanceof GrVariable &&
           ((GrVariable)parent).getNameIdentifierGroovy() == position &&
           ((GrVariable)parent).getTypeElementGroovy() == null &&
           pparent instanceof GrVariableDeclaration &&
           ((GrVariableDeclaration)pparent).isTuple();
  }

  public static void processVariants(GrReferenceElement referenceElement, PrefixMatcher matcher, CompletionParameters parameters, Consumer<LookupElement> consumer) {
    if (referenceElement instanceof GrCodeReferenceElementImpl) {
      CompleteCodeReferenceElement.processVariants((GrCodeReferenceElementImpl)referenceElement, consumer, matcher);
    }
    else if (referenceElement instanceof GrReferenceExpressionImpl) {
      CompleteReferenceExpression.processVariants(matcher, consumer, (GrReferenceExpressionImpl)referenceElement, parameters);
    }
  }

  public static boolean isInPossibleClosureParameter(PsiElement position) { //Closure cl={String x, <caret>...
    if (position == null) return false;

    if (position instanceof PsiWhiteSpace || position.getNode().getElementType() == GroovyTokenTypes.mNLS) {
      position = FilterPositionUtil.searchNonSpaceNonCommentBack(position);
    }

    boolean hasCommas = false;
    while (position != null) {
      PsiElement parent = position.getParent();
      if (parent instanceof GrVariable) {
        PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(parent);
        hasCommas = prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA;
      }

      if (parent instanceof GrClosableBlock) {
        PsiElement sibling = position.getPrevSibling();
        while (sibling != null) {
          if (sibling instanceof GrParameterList) {
            return hasCommas;
          }

          boolean isComma = sibling instanceof LeafPsiElement && GroovyTokenTypes.mCOMMA == ((LeafPsiElement)sibling).getElementType();
          hasCommas |= isComma;

          if (isComma ||
              sibling instanceof PsiWhiteSpace ||
              sibling instanceof PsiErrorElement ||
              sibling instanceof GrVariableDeclaration ||
              sibling instanceof GrReferenceExpression && !((GrReferenceExpression)sibling).isQualified()
            ) {
            sibling = sibling.getPrevSibling();
          }
          else {
            return false;
          }
        }
        return false;
      }
      position = parent;
    }
    return false;
  }
}
