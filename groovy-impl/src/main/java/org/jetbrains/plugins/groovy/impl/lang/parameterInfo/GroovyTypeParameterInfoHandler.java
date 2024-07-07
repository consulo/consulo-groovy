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

import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.PsiTypeParameterListOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.parameterInfo.*;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyTypeParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GrTypeArgumentList, PsiTypeParameter, GrTypeElement> {

  private static final Set<Class<?>> ALLOWED_PARENT_CLASSES = Set.of(GrCodeReferenceElement.class);
  private static final Set<Class<?>> STOP_SEARCHING_CLASSES = Set.of(GroovyFile.class);

  @Nonnull
  @Override
  public GrTypeElement[] getActualParameters(@Nonnull GrTypeArgumentList o) {
    return o.getTypeArgumentElements();
  }

  @Nonnull
  @Override
  public IElementType getActualParameterDelimiterType() {
    return GroovyTokenTypes.mCOMMA;
  }

  @Nonnull
  @Override
  public IElementType getActualParametersRBraceType() {
    return GroovyTokenTypes.mGT;
  }

  @Nonnull
  @Override
  public Set<Class<?>> getArgumentListAllowedParentClasses() {
    return ALLOWED_PARENT_CLASSES;
  }

  @Nonnull
  @Override
  public Set<? extends Class<?>> getArgListStopSearchClasses() {
    return STOP_SEARCHING_CLASSES;
  }

  @Nonnull
  @Override
  public Class<GrTypeArgumentList> getArgumentListClass() {
    return GrTypeArgumentList.class;
  }

  @Override
  public boolean couldShowInLookup() {
    return false;
  }

  @Nullable
  @Override
  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    return null;
  }


  @Nullable
  @Override
  public GrTypeArgumentList findElementForParameterInfo(CreateParameterInfoContext context) {
    final GrTypeArgumentList parameterList =
      ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), GrTypeArgumentList.class);

    if (parameterList != null) {
      if (!(parameterList.getParent() instanceof GrCodeReferenceElement)) return null;
      final GrCodeReferenceElement ref = ((GrCodeReferenceElement)parameterList.getParent());

      final PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiTypeParameterListOwner)) return null;

      final PsiTypeParameter[] typeParams = ((PsiTypeParameterListOwner)resolved).getTypeParameters();
      if (typeParams.length == 0) return null;

      context.setItemsToShow(typeParams);
      return parameterList;
    }

    return null;
  }

  @Override
  public void showParameterInfo(@Nonnull GrTypeArgumentList element, CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Nullable
  @Override
  public GrTypeArgumentList findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), GrTypeArgumentList.class);
  }

  @Override
  public void updateParameterInfo(@Nonnull GrTypeArgumentList o, UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(o.getNode(), context.getOffset(), getActualParameterDelimiterType());
    context.setCurrentParameter(index);
    final Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length && index >= 0 ? (PsiElement)objectsToView[index] : null);
  }

  @Override
  public void updateUI(PsiTypeParameter p, ParameterInfoUIContext context) {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(p.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(Arrays.asList(p.getSuperTypes()), t -> t.getPresentableText(), ", "));

    context.setupUIComponentPresentation(StringUtil.escapeXml(buffer.toString()),
                                         0,
                                         highlightEndOffset,
                                         false,
                                         false,
                                         false,
                                         context.getDefaultParameterColor());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
