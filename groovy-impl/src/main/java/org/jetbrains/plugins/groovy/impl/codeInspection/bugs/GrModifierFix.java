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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.language.psi.*;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * @author Max Medvedev
 */
public class GrModifierFix extends GroovyFix {
  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST = descriptor -> {
    final PsiElement element = descriptor.getPsiElement();
    assert element instanceof PsiModifierList : element;
    return (PsiModifierList)element;
  };

  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST_OWNER = descriptor -> {
    final PsiElement element = descriptor.getPsiElement();
    assert element instanceof PsiModifierListOwner : element;
    return ((PsiModifierListOwner)element).getModifierList();
  };


  private final String myModifier;
  private final String myText;
  private final boolean myDoSet;
  private final Function<ProblemDescriptor, PsiModifierList> myModifierListProvider;

  public GrModifierFix(@Nonnull PsiVariable member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean doSet,
                       @Nonnull Function<ProblemDescriptor, PsiModifierList> modifierListProvider) {
    myModifier = modifier;
    myDoSet = doSet;
    myModifierListProvider = modifierListProvider;
    myText = initText(doSet, member.getName(), modifier);
  }

  public GrModifierFix(@Nonnull PsiMember member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet,
                       @Nonnull Function<ProblemDescriptor, PsiModifierList> modifierListProvider) {
    myModifier = modifier;
    myDoSet = doSet;
    myModifierListProvider = modifierListProvider;
    myText = initText(doSet, getMemberName(member, showContainingClass), modifier);
  }

  public static String initText(boolean doSet, @Nonnull String name, @Nonnull String modifier) {
    return GroovyBundle.message(doSet ? "change.modifier" : "change.modifier.not", name,
                                toPresentableText(modifier));
  }

  private static String getMemberName(PsiMember member, boolean showContainingClass) {
    if (showContainingClass) {
      final PsiClass containingClass = member.getContainingClass();
      String containingClassName = containingClass != null ? containingClass.getName() + "." : "";
      return containingClassName + member.getName();
    }
    else {
      return member.getName();
    }
  }

  public static String toPresentableText(String modifier) {
    return GroovyBundle.message(modifier + ".visibility.presentation");
  }

  @Nonnull
  @Override
  public String getName() {
    return myText;
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiModifierList modifierList = getModifierList(descriptor);
    modifierList.setModifierProperty(myModifier, myDoSet);
  }

  private PsiModifierList getModifierList(ProblemDescriptor descriptor) {
    return myModifierListProvider.apply(descriptor);
  }
}
