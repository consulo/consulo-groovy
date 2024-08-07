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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.parameterInfo.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyAnnotationAttributeInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GrAnnotationArgumentList, PsiAnnotationMethod, GrAnnotationNameValuePair> {

  private static final Set<Class<?>> ALLOWED_CLASSES = Set.of(GrAnnotation.class);
  private static final Set<Class<GroovyFile>> STOP_SEARCHING_CLASSES = Collections.singleton(GroovyFile.class);

  @Nonnull
  @Override
  public GrAnnotationNameValuePair[] getActualParameters(@Nonnull GrAnnotationArgumentList o) {
    return o.getAttributes();
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

  @Nonnull
  @Override
  public Set<Class<?>> getArgumentListAllowedParentClasses() {
    return ALLOWED_CLASSES;
  }

  @Nonnull
  @Override
  public Set<? extends Class<?>> getArgListStopSearchClasses() {
    return STOP_SEARCHING_CLASSES;
  }

  @Nonnull
  @Override
  public Class<GrAnnotationArgumentList> getArgumentListClass() {
    return GrAnnotationArgumentList.class;
  }

  @Override
  public boolean couldShowInLookup() {
    return true;
  }

  @Override
  public Object[] getParametersForLookup(@Nonnull LookupElement item, @Nonnull ParameterInfoContext context) {
    Object o = item.getObject();

    if (o instanceof GroovyResolveResult) {
      o = ((GroovyResolveResult)o).getElement();
    }


    if (o instanceof PsiAnnotationMethod) {
      return ((PsiAnnotationMethod)o).getParameterList().getParameters();
    }
    else {
      return GrAnnotationNameValuePair.EMPTY_ARRAY;
    }
  }

  @Override
  public GrAnnotationArgumentList findElementForParameterInfo(@Nonnull CreateParameterInfoContext context) {
    return findAnchor(context.getEditor(), context.getFile());
  }

  @Nullable
  private static GrAnnotationArgumentList findAnchor(@Nonnull final Editor editor, @Nonnull final PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;

    return PsiTreeUtil.getParentOfType(element, GrAnnotationArgumentList.class);
  }

  @Override
  public void showParameterInfo(@Nonnull GrAnnotationArgumentList argumentList, @Nonnull CreateParameterInfoContext context) {
    final GrAnnotation parent = DefaultGroovyMethods.asType(argumentList.getParent(), GrAnnotation.class);

    final PsiElement resolved = parent.getClassReference().resolve();
    if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
      final PsiMethod[] methods = ((PsiClass)resolved).getMethods();
      context.setItemsToShow(methods);
      context.showHint(argumentList, argumentList.getTextRange().getStartOffset(), this);
      final PsiAnnotationMethod currentMethod = findAnnotationMethod(context.getFile(), context.getEditor());
      if (currentMethod != null) {
        context.setHighlightedElement(currentMethod);
      }
    }
  }

  @Nullable
  private static PsiAnnotationMethod findAnnotationMethod(@Nonnull PsiFile file, @Nonnull Editor editor) {
    PsiNameValuePair pair = ParameterInfoUtils.findParentOfType(file, inferOffset(editor), PsiNameValuePair.class);
    if (pair == null) return null;
    final PsiReference reference = pair.getReference();
    final PsiElement resolved = reference != null ? reference.resolve() : null;
    return PsiUtil.isAnnotationMethod(resolved) ? (PsiAnnotationMethod)resolved : null;
  }

  @Override
  public GrAnnotationArgumentList findElementForUpdatingParameterInfo(@Nonnull UpdateParameterInfoContext context) {
    return findAnchor(context.getEditor(), context.getFile());
  }

  @Override
  public void updateParameterInfo(@Nonnull GrAnnotationArgumentList o, @Nonnull UpdateParameterInfoContext context) {
    context.setHighlightedParameter(findAnnotationMethod(context.getFile(), context.getEditor()));
  }

  private static int inferOffset(@Nonnull final Editor editor) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftForward(chars, editor.getCaretModel().getOffset(), " \t");
    final char character = chars.charAt(offset1);
    if (character == ',' || character == ')') {
      offset1 = CharArrayUtil.shiftBackward(chars, offset1 - 1, " \t");
    }
    return offset1;
  }

  @Override
  public void updateUI(@Nonnull PsiAnnotationMethod p, @Nonnull ParameterInfoUIContext context) {
    @NonNls StringBuilder buffer = new StringBuilder();
    final PsiType returnType = p.getReturnType();
    assert returnType != null;
    buffer.append(returnType.getPresentableText());
    buffer.append(" ");
    int highlightStartOffset = buffer.length();
    buffer.append(p.getName());
    int highlightEndOffset = buffer.length();
    buffer.append("()");

    final PsiAnnotationMemberValue defaultValue = p.getDefaultValue();
    if (defaultValue != null) {
      buffer.append(" default ");
      buffer.append(defaultValue.getText());
    }


    context.setupUIComponentPresentation(StringUtil.escapeXml(buffer.toString()),
                                         highlightStartOffset,
                                         highlightEndOffset,
                                         false,
                                         p.isDeprecated(),
                                         false,
                                         context.getDefaultParameterColor());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
