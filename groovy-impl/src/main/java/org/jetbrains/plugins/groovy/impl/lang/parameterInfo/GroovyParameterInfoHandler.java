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
package org.jetbrains.plugins.groovy.impl.lang.parameterInfo;

import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.parameterInfo.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.impl.lang.documentation.TypePresentation;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrRecursiveSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ven
 */
@ExtensionImpl
public class GroovyParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GroovyPsiElement, Object, GroovyPsiElement> {
  private static final Logger LOG = Logger.getInstance(GroovyParameterInfoHandler.class);

  public boolean couldShowInLookup() {
    return true;
  }

  private static final Set<? extends Class<?>> ourStopSearch = Collections.singleton(GrMethod.class);

  @Nonnull
  @Override
  public Set<? extends Class<?>> getArgListStopSearchClasses() {
    return ourStopSearch;
  }

  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    List<? extends PsiElement> elements = JavaCompletionUtil.getAllPsiElements(item);

    if (elements != null) {
      List<GroovyResolveResult> methods = new ArrayList<GroovyResolveResult>();
      for (PsiElement element : elements) {
        if (element instanceof PsiMethod) {
          methods.add(new GroovyResolveResultImpl(element, true));
        }
      }
      return ArrayUtil.toObjectArray(methods);
    }

    return null;
  }

  public GroovyPsiElement findElementForParameterInfo(CreateParameterInfoContext context) {
    return findAnchorElement(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  public GroovyPsiElement findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
    return findAnchorElement(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  @Nullable
  private static GroovyPsiElement findAnchorElement(int offset, PsiFile file) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    GroovyPsiElement argList = PsiTreeUtil.getParentOfType(element, GrArgumentList.class);
    if (argList != null) return argList;
    GrCall call = PsiTreeUtil.getParentOfType(element, GrCall.class);
    if (call != null) {
      argList = call.getArgumentList();
      if (argList != null && argList.getTextRange().contains(element.getTextRange().getStartOffset())) return argList;
    } else {
      offset = CharArrayUtil.shiftBackward(file.getText(), offset, "\n\t ");
      if (offset <= 0) return null;
      element = file.findElementAt(offset);
      if (element != null && element.getParent() instanceof GrReferenceExpression)
        return (GroovyPsiElement) element.getParent();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public void showParameterInfo(@Nonnull GroovyPsiElement place, @Nonnull CreateParameterInfoContext context) {
    GroovyResolveResult[] variants = ResolveUtil.getCallVariants(place);

    List elementToShow = new ArrayList();
    PsiElement parent = place.getParent();
    if (parent instanceof GrMethodCall) {
      GrExpression invoked = ((GrMethodCall)parent).getInvokedExpression();
      if (isPropertyOrVariableInvoked(invoked)) {
        PsiType type = invoked.getType();
        if (type instanceof GrClosureType) {
          addSignatureVariant(elementToShow, (GrClosureType)type);
        }
        else if (type != null) {
          addMethodAndClosureVariants(elementToShow,
                                      ResolveUtil.getMethodCandidates(type, "call", invoked, PsiUtil.getArgumentTypes(place, true)));
        }
      }
      else {
        addMethodAndClosureVariants(elementToShow, variants);
      }
    }
    else {
      elementToShow.addAll(Arrays.asList(variants));
    }
    
    filterOutReflectedMethods(elementToShow);
    context.setItemsToShow(ArrayUtil.toObjectArray(elementToShow));
    context.showHint(place, place.getTextRange().getStartOffset(), this);
  }

  private static void addMethodAndClosureVariants(@Nonnull List<Object> elementToShow, @Nonnull GroovyResolveResult[] variants) {
    for (GroovyResolveResult variant : variants) {
      PsiElement element = variant.getElement();
      if (element instanceof PsiMethod) {
        elementToShow.add(variant);
      }
      else if (element instanceof GrVariable) {
        PsiType type = ((GrVariable)element).getTypeGroovy();
        if (type instanceof GrClosureType) {
          addSignatureVariant(elementToShow, (GrClosureType)type);
        }
      }
    }
  }

  private static void addSignatureVariant(@Nonnull final List<Object> elementToShow, @Nonnull GrClosureType type) {
    type.getSignature().accept(new GrRecursiveSignatureVisitor() {
      @Override
      public void visitClosureSignature(GrClosureSignature signature) {
        elementToShow.add(signature);
      }
    });
  }

  private static void filterOutReflectedMethods(List toShow) {
    Set<GrMethod> methods = new HashSet<GrMethod>();

    for (Iterator iterator = toShow.iterator(); iterator.hasNext(); ) {
      Object next = iterator.next();
      if (next instanceof GroovyResolveResult) {
        PsiElement element = ((GroovyResolveResult)next).getElement();
        if (element instanceof GrReflectedMethod) {
          GrMethod base = ((GrReflectedMethod)element).getBaseMethod();
          if (!methods.add(base)) {
            iterator.remove();
          }
        }
      }
    }
  }

  private static boolean isPropertyOrVariableInvoked(GrExpression invoked) {
    if (!(invoked instanceof GrReferenceExpression)) return false;

    GroovyResolveResult resolveResult = ((GrReferenceExpression)invoked).advancedResolve();
    return resolveResult.isInvokedOnProperty() || resolveResult.getElement() instanceof PsiVariable;
  }

  public void updateParameterInfo(@Nonnull GroovyPsiElement place, UpdateParameterInfoContext context) {
    PsiElement parameterOwner = context.getParameterOwner();
    if (parameterOwner != place) {
      context.removeHint();
      return;
    }

    int offset = context.getEditor().getCaretModel().getOffset();
    offset = CharArrayUtil.shiftForward(context.getEditor().getDocument().getText(), offset, " \t\n");
    int currIndex = getCurrentParameterIndex(place, offset);
    context.setCurrentParameter(currIndex);
    Object[] objects = context.getObjectsToView();

    Outer:
    for (int i = 0; i < objects.length; i++) {
      PsiType[] parameterTypes = null;
      PsiType[] argTypes = null;
      PsiSubstitutor substitutor = null;
      if (objects[i] instanceof GroovyResolveResult) {
        GroovyResolveResult resolveResult = (GroovyResolveResult)objects[i];
        PsiNamedElement namedElement = (PsiNamedElement)resolveResult.getElement();
        if (namedElement instanceof GrReflectedMethod) namedElement = ((GrReflectedMethod)namedElement).getBaseMethod();

        substitutor = resolveResult.getSubstitutor();
        assert namedElement != null;
        if (!namedElement.isValid()) {
          context.setUIComponentEnabled(i, false);
          continue Outer;
        }
        if (namedElement instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)namedElement;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          parameterTypes = new PsiType[parameters.length];
          for (int j = 0; j < parameters.length; j++) {
            parameterTypes[j] = parameters[j].getType();
          }
          argTypes = PsiUtil.getArgumentTypes(place, false);
        }
        if (argTypes == null) continue;
      }
      else if (objects[i] instanceof GrClosureSignature) {
        GrClosureSignature signature = (GrClosureSignature)objects[i];
        argTypes = PsiUtil.getArgumentTypes(place, false);
        parameterTypes = new PsiType[signature.getParameterCount()];
        int j = 0;
        for (GrClosureParameter parameter : signature.getParameters()) {
          parameterTypes[j++] = parameter.getType();
        }
      }
      else {
        continue Outer;
      }

      assert argTypes != null;
      if (argTypes.length > currIndex) {
        if (parameterTypes.length <= currIndex) {
          context.setUIComponentEnabled(i, false);
          continue;
        }
        else {
          for (int j = 0; j < currIndex; j++) {
            PsiType argType = argTypes[j];
            PsiType paramType = substitutor != null ? substitutor.substitute(parameterTypes[j]) : parameterTypes[j];
            if (!TypesUtil.isAssignableByMethodCallConversion(paramType, argType, place)) {
              context.setUIComponentEnabled(i, false);
              break Outer;
            }
          }
        }
      }

      context.setUIComponentEnabled(i, true);
    }
  }

  private static int getCurrentParameterIndex(GroovyPsiElement place, int offset) {
    if (place instanceof GrArgumentList) {
      GrArgumentList list = (GrArgumentList)place;

      int idx = (list.getNamedArguments().length > 0) ? 1 : 0;
      for (PsiElement child = list.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getTextRange().contains(offset)) {
          if (child instanceof GrNamedArgument) return 0;
          return idx;
        }

        if (child.getNode().getElementType() == GroovyTokenTypes.mCOMMA) idx++;
        if (isNamedArgWithPriorComma(child)) idx--;
      }
    }
    return -1;
  }

  private static boolean isNamedArgWithPriorComma(PsiElement child) {
    if (!(child instanceof GrNamedArgument)) return false;
    PsiElement element = PsiUtil.skipWhitespacesAndComments(child.getPrevSibling(), false);
    return element != null && element.getNode().getElementType() == GroovyTokenTypes.mCOMMA;
  }

  public void updateUI(Object o, ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (o == null) return;

    Object element;
    if (o instanceof GroovyResolveResult) {
      element = ((GroovyResolveResult)o).getElement();
      if (element == null || !((PsiElement)element).isValid()) {
        context.setUIComponentEnabled(false);
        return;
      }
    }
    else if (o instanceof GrClosureSignature) {
      if (!((GrClosureSignature)o).isValid()) {
        context.setUIComponentEnabled(false);
        return;
      }
      element = o;
    }
    else {
      return;
    }

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    int currentParameter = context.getCurrentParameterIndex();

    StringBuilder buffer = new StringBuilder();


    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method instanceof GrReflectedMethod) method = ((GrReflectedMethod)method).getBaseMethod();

      if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
        if (!method.isConstructor()) {
          PsiType returnType = PsiUtil.getSmartReturnType(method);
          if (returnType != null) {
            buffer.append(returnType.getPresentableText());
            buffer.append(' ');
          }
        }
        buffer.append(method.getName());
        buffer.append('(');
      }

      PsiParameter[] params = method.getParameterList().getParameters();

      int numParams = params.length;
      if (numParams > 0) {
        LOG.assertTrue(o instanceof GroovyResolveResult, o.getClass());
        PsiSubstitutor substitutor = ((GroovyResolveResult)o).getSubstitutor();
        for (int j = 0; j < numParams; j++) {
          PsiParameter param = params[j];

          int startOffset = buffer.length();

          appendParameterText(param, substitutor, buffer);

          int endOffset = buffer.length();

          if (j < numParams - 1) {
            buffer.append(", ");
          }

          if (context.isUIComponentEnabled() &&
              (j == currentParameter || (j == numParams - 1 && param.isVarArgs() && currentParameter >= numParams))) {
            highlightStartOffset = startOffset;
            highlightEndOffset = endOffset;
          }
        }
      } else {
        buffer.append("no parameters");
      }

      if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
        buffer.append(")");
      }

    } else if (element instanceof PsiClass) {
      buffer.append("no parameters");
    }
    else if (element instanceof GrClosureSignature) {
      GrClosureParameter[] parameters = ((GrClosureSignature)element).getParameters();
      if (parameters.length > 0) {
        for (int i = 0; i < parameters.length; i++) {
          if (i > 0) buffer.append(", ");

          int startOffset = buffer.length();
          PsiType psiType = parameters[i].getType();
          if (psiType == null) {
            buffer.append("def");
          }
          else {
            buffer.append(psiType.getPresentableText());
          }
          int endOffset = buffer.length();

          if (context.isUIComponentEnabled() &&
              (i == currentParameter || (i == parameters.length - 1 && ((GrClosureSignature)element).isVarargs() && currentParameter >= parameters.length))) {
            highlightStartOffset = startOffset;
            highlightEndOffset = endOffset;
          }

          GrExpression initializer = parameters[i].getDefaultInitializer();
          if (initializer != null) {
            buffer.append(" = ").append(initializer.getText());
          }
        }
      }
      else {
        buffer.append("no parameters");
      }
    }

    boolean isDeprecated = o instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) o).isDeprecated();

    context.setupUIComponentPresentation(
      StringUtil.escapeXml(buffer.toString()),
      highlightStartOffset,
      highlightEndOffset,
      !context.isUIComponentEnabled(),
      isDeprecated,
      false,
      context.getDefaultParameterColor()
    );
  }

  private static void appendParameterText(PsiParameter param, PsiSubstitutor substitutor, StringBuilder buffer) {
    if (param instanceof GrParameter) {
      GrParameter grParam = (GrParameter)param;
      GroovyPresentationUtil.appendParameterPresentation(grParam, substitutor, TypePresentation.PRESENTABLE, buffer);

      GrExpression initializer = grParam.getInitializerGroovy();
      if (initializer != null) {
        buffer.append(" = ").append(initializer.getText());
      }
      else if (grParam.isOptional()) {
        buffer.append(" = null");
      }
    } else {
      PsiType t = param.getType();
      PsiType paramType = substitutor.substitute(t);
      buffer.append(paramType.getPresentableText());
      String name = param.getName();
      if (name != null) {
        buffer.append(" ");
        buffer.append(name);
      }
    }
  }

  @Nonnull
  @Override
  public GroovyPsiElement[] getActualParameters(@Nonnull GroovyPsiElement o) {
    if (o instanceof GrArgumentList) return ((GrArgumentList)o).getAllArguments();
    return GroovyPsiElement.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public IElementType getActualParameterDelimiterType() {
    return GroovyTokenTypes.mCOMMA;
  }

  @Nonnull
  @Override
  public IElementType getActualParametersRBraceType() {
    return GroovyTokenTypes.mRPAREN;
  }

  private static final Set<Class<?>> ALLOWED_PARAM_CLASSES = Collections.<Class<?>>singleton(GroovyPsiElement.class);

  @Nonnull
  @Override
  public Set<Class<?>> getArgumentListAllowedParentClasses() {
    return ALLOWED_PARAM_CLASSES;
  }

  @Nonnull
  @Override
  public Class<GroovyPsiElement> getArgumentListClass() {
    return GroovyPsiElement.class;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
