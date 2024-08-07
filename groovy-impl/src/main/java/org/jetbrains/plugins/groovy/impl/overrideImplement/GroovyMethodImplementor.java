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
package org.jetbrains.plugins.groovy.impl.overrideImplement;

import com.intellij.java.impl.codeInsight.MethodImplementor;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.actions.generate.GroovyGenerationInfo;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;

import jakarta.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author Medvedev Max
 */
@ExtensionImpl
public class GroovyMethodImplementor implements MethodImplementor {
  @Nonnull
  @Override
  public PsiMethod[] getMethodsToImplement(PsiClass aClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiMethod[] createImplementationPrototypes(PsiClass inClass, PsiMethod method) throws IncorrectOperationException {
    if (!(inClass instanceof GrTypeDefinition)) {
      return PsiMethod.EMPTY_ARRAY;
    }
    if (method instanceof GrTraitMethod) {
      return PsiMethod.EMPTY_ARRAY;
    }

    final PsiClass containingClass = method.getContainingClass();
    PsiSubstitutor substitutor = inClass.isInheritor(containingClass, true) ? TypeConversionUtil.getSuperClassSubstitutor(containingClass,
                                                                                                                          inClass,
                                                                                                                          PsiSubstitutor.EMPTY) : PsiSubstitutor.EMPTY;
    return new PsiMethod[]{GroovyOverrideImplementUtil.generateMethodPrototype((GrTypeDefinition)inClass, method, substitutor)};
  }

  @Override
  public GenerationInfo createGenerationInfo(PsiMethod method, boolean mergeIfExists) {
    if (method instanceof GrMethod) {
      return new GroovyGenerationInfo<GrMethod>((GrMethod)method, mergeIfExists);
    }
    return null;
  }

  @Nonnull
  @Override
  public Consumer<PsiMethod> createDecorator(final PsiClass targetClass,
                                             final PsiMethod baseMethod,
                                             final boolean toCopyJavaDoc,
                                             final boolean insertOverrideIfPossible) {
    return new PsiMethodConsumer(targetClass, toCopyJavaDoc, baseMethod, insertOverrideIfPossible);
  }

  static class PsiMethodConsumer implements Consumer<PsiMethod> {
    private final PsiClass myTargetClass;
    private final boolean myToCopyJavaDoc;
    private final PsiMethod myBaseMethod;
    private final boolean myInsertOverrideIfPossible;

    public PsiMethodConsumer(PsiClass targetClass, boolean toCopyJavaDoc, PsiMethod baseMethod, boolean insertOverrideIfPossible) {
      myTargetClass = targetClass;
      myToCopyJavaDoc = toCopyJavaDoc;
      myBaseMethod = baseMethod;
      myInsertOverrideIfPossible = insertOverrideIfPossible;
    }

    @Override
    public void accept(PsiMethod method) {
      Project project = myTargetClass.getProject();

      if (myToCopyJavaDoc) {
        PsiDocComment baseMethodDocComment = myBaseMethod.getDocComment();
        if (baseMethodDocComment != null) {
          GrDocComment docComment = GroovyPsiElementFactory.getInstance(project).createDocCommentFromText(baseMethodDocComment.getText());
          GrDocCommentUtil.setDocComment(((GrMethod)method), docComment);
        }
      }
      else {
        PsiDocComment docComment = method.getDocComment();
        if (docComment != null) {
          docComment.delete();
        }
      }

      if (myInsertOverrideIfPossible) {
        if (OverrideImplementUtil.canInsertOverride(method, myTargetClass) &&
          JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OVERRIDE, myTargetClass.getResolveScope()) != null &&
          method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) == null) {
          method.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
        }
      }
      else {
        PsiAnnotation annotation = method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
        if (annotation != null) {
          annotation.delete();
        }
      }
    }
  }
}
