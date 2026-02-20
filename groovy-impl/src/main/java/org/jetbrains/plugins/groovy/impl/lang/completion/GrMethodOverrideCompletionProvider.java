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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.application.AllIcons;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.ui.image.ImageEffects;
import org.jetbrains.plugins.groovy.impl.lang.completion.handlers.GroovyMethodOverrideHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.impl.overrideImplement.GroovyOverrideImplementExploreUtil;

import jakarta.annotation.Nonnull;
import java.util.Collection;

import static consulo.language.pattern.PlatformPatterns.psiComment;
import static consulo.language.pattern.PlatformPatterns.psiElement;

class GrMethodOverrideCompletionProvider implements CompletionProvider
{

  private static final ElementPattern<PsiElement> PLACE = psiElement().withParent(GrTypeDefinitionBody.class).with(
    new PatternCondition<PsiElement>("Not in extends/implements clause of inner class") {
      @Override
      public boolean accepts(@Nonnull PsiElement element, ProcessingContext context) {
        GrTypeDefinition innerDefinition = PsiTreeUtil.getPrevSiblingOfType(element, GrTypeDefinition.class);
        return innerDefinition == null || innerDefinition.getContainingClass() == null || innerDefinition.getBody() != null;
      }
    }).andNot(psiComment());

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    GrTypeDefinition currentClass = PsiTreeUtil.getParentOfType(position, GrTypeDefinition.class);

    if (currentClass != null) {
      addSuperMethods(currentClass, result, false);
      addSuperMethods(currentClass, result, true);
    }
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PLACE, new GrMethodOverrideCompletionProvider());
  }

  private static void addSuperMethods(GrTypeDefinition psiClass, CompletionResultSet completionResultSet, boolean toImplement) {
    Collection<CandidateInfo> candidates = GroovyOverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, toImplement);
    for (CandidateInfo candidateInfo : candidates) {
      PsiMethod method = (PsiMethod)candidateInfo.getElement();
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
