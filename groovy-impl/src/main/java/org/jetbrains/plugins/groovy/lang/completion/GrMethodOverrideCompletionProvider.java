/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.VisibilityUtil;
import consulo.codeInsight.completion.CompletionProvider;
import consulo.ide.IconDescriptorUpdaters;
import consulo.ui.image.ImageEffects;
import org.jetbrains.plugins.groovy.lang.completion.handlers.GroovyMethodOverrideHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementExploreUtil;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiComment;
import static com.intellij.patterns.PlatformPatterns.psiElement;

class GrMethodOverrideCompletionProvider implements CompletionProvider
{

  private static final ElementPattern<PsiElement> PLACE = psiElement().withParent(GrTypeDefinitionBody.class).with(
    new PatternCondition<PsiElement>("Not in extends/implements clause of inner class") {
      @Override
      public boolean accepts(@Nonnull PsiElement element, ProcessingContext context) {
        final GrTypeDefinition innerDefinition = PsiTreeUtil.getPrevSiblingOfType(element, GrTypeDefinition.class);
        return innerDefinition == null || innerDefinition.getContainingClass() == null || innerDefinition.getBody() != null;
      }
    }).andNot(psiComment());

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final GrTypeDefinition currentClass = PsiTreeUtil.getParentOfType(position, GrTypeDefinition.class);

    if (currentClass != null) {
      addSuperMethods(currentClass, result, false);
      addSuperMethods(currentClass, result, true);
    }
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PLACE, new GrMethodOverrideCompletionProvider());
  }

  private static void addSuperMethods(final GrTypeDefinition psiClass, CompletionResultSet completionResultSet, boolean toImplement) {
    final Collection<CandidateInfo> candidates = GroovyOverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, toImplement);
    for (CandidateInfo candidateInfo : candidates) {
      final PsiMethod method = (PsiMethod)candidateInfo.getElement();
      if (method.isConstructor()) continue;

      PsiSubstitutor substitutor = candidateInfo.getSubstitutor();
      String parameters = PsiFormatUtil.formatMethod(method, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME);
      String visibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
      String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL ? "" : visibility + " ");
      PsiType type = substitutor.substitute(method.getReturnType());
      String parentClassName = psiClass == null ? "" : psiClass.getName();
      String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + method.getName();

      LookupElementBuilder lookupElement = LookupElementBuilder.create(method, signature)
        .appendTailText(parameters, false)
        .appendTailText("{...}", true)
        .withTypeText(parentClassName)
        .withIcon(ImageEffects.appendRight(IconDescriptorUpdaters.getIcon(method, 0), toImplement ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod))
        .withInsertHandler(new GroovyMethodOverrideHandler(psiClass));
      completionResultSet.addElement(lookupElement);
    }
  }
}
