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
package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.java.language.impl.psi.impl.light.LightMethod;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiMirrorElement;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class MixinMemberContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@Nonnull PsiType qualifierType,
                                     @Nonnull PsiScopeProcessor processor,
                                     @Nonnull PsiElement place,
                                     @Nonnull ResolveState state) {
    if (isInAnnotation(place)) return;

    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(qualifierType);
    if (aClass == null) return;

    PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList == null) return;

    List<PsiClass> mixins = new ArrayList<PsiClass>();
    for (PsiAnnotation annotation : getAllMixins(modifierList)) {
      PsiAnnotationMemberValue value = annotation.findAttributeValue("value");

      if (value instanceof GrAnnotationArrayInitializer) {
        GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          addMixin(initializer, mixins);
        }
      }
      else if (value instanceof GrExpression) {
        addMixin((GrExpression)value, mixins);
      }
    }

    MixinProcessor delegate = new MixinProcessor(processor, qualifierType, place);
    for (PsiClass mixin : mixins) {
      if (!mixin.processDeclarations(delegate, state, null, place)) {
        return;
      }
    }
  }

  public static String getOriginInfoForCategory(PsiMethod element) {
    PsiClass aClass = element.getContainingClass();
    if (aClass != null && aClass.getName() != null) {
      return "mixed in from " + aClass.getName();
    }
    return "mixed in";
  }

  public static String getOriginInfoForMixin(@Nonnull PsiType subjectType) {
    return "mixed in " + subjectType.getPresentableText();
  }

  private static List<PsiAnnotation> getAllMixins(PsiModifierList modifierList) {
    ArrayList<PsiAnnotation> result = new ArrayList<PsiAnnotation>();
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (GroovyCommonClassNames.GROOVY_LANG_MIXIN.equals(annotation.getQualifiedName())) {
        result.add(annotation);
      }
    }
    return result;
  }

  private static boolean isInAnnotation(PsiElement place) {
    return place.getParent() instanceof GrAnnotation || place.getParent() instanceof GrAnnotationArrayInitializer;
  }

  private static void addMixin(GrAnnotationMemberValue value, List<PsiClass> mixins) {
    if (value instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)value).resolve();
      if (resolved instanceof PsiClass) {
        mixins.add((PsiClass)resolved);
      }
    }
  }

  private static class MixinedMethod extends LightMethod implements OriginInfoAwareElement, PsiMirrorElement {
    private final String myOriginInfo;
    private final PsiMethod myPrototype;

    public MixinedMethod(@Nonnull PsiMethod method, String originInfo) {
      super(method.getManager(), method, ObjectUtil.assertNotNull(method.getContainingClass()));
      myOriginInfo = originInfo;
      myPrototype = method;
    }

    @Nullable
    @Override
    public String getOriginInfo() {
      return myOriginInfo;
    }

    @Nonnull
    @Override
    public PsiElement getPrototype() {
      return myPrototype;
    }
  }

  public static class MixinProcessor extends DelegatingScopeProcessor {
    private final PsiType myType;
    private final PsiElement myPlace;

    public MixinProcessor(PsiScopeProcessor delegate, @Nonnull PsiType qualifierType, @Nullable PsiElement place) {
      super(delegate);
      myType = qualifierType;
      myPlace = place;
    }

    @Override
    public boolean execute(@Nonnull PsiElement element, ResolveState state) {
      if (element instanceof PsiMethod && GdkMethodUtil.isCategoryMethod((PsiMethod)element, myType, myPlace, state.get(PsiSubstitutor.KEY))) {
        PsiMethod method = (PsiMethod)element;
        String originInfo = getOriginInfoForCategory(method);
        return super.execute(GrGdkMethodImpl.createGdkMethod(method, false, originInfo), state);
      }
      else if (element instanceof PsiMethod) {
        return super.execute(new MixinedMethod((PsiMethod)element, getOriginInfoForMixin(myType)), state);
      }
      else {
        return super.execute(element, state);
      }
    }
  }
}
