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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class ClosureToSamConverter extends GrTypeConverter {
  private static final LightCacheKey<Ref<MethodSignature>> SAM_SIGNATURE_LIGHT_CACHE_KEY = LightCacheKey.createByJavaModificationCount();

  @Override
  public boolean isAllowedInMethodCall() {
    return true;
  }

  @Override
  public Boolean isConvertible(@Nonnull PsiType ltype, @Nonnull PsiType rtype, @Nonnull final GroovyPsiElement context) {
    if (rtype instanceof GrClosureType &&
        ltype instanceof PsiClassType &&
        isSamConversionAllowed(context) &&
        !TypesUtil.isClassType(ltype, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      MethodSignature signature = findSAMSignature(ltype);
      if (signature != null) {
        final PsiType[] samParameterTypes = signature.getParameterTypes();

        GrSignature closureSignature = ((GrClosureType)rtype).getSignature();

        boolean raw = ((PsiClassType)ltype).isRaw();
        if (raw) return true;

        if (GrClosureSignatureUtil.isSignatureApplicable(closureSignature, samParameterTypes, context)) {
          return true;
        }
      }
    }

    return null;
  }

  public static boolean isSamConversionAllowed(PsiElement context) {
    return GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY2_2);
  }

  @Nullable
  public static MethodSignature findSingleAbstractMethod(@Nonnull PsiClass aClass, @Nonnull PsiSubstitutor substitutor) {
    MethodSignature signature;
    Ref<MethodSignature> cached = SAM_SIGNATURE_LIGHT_CACHE_KEY.getCachedValue(aClass);
    if (cached != null) {
      signature = cached.get();
    }
    else {
      Ref<MethodSignature> newCached = Ref.create(doFindSingleAbstractMethodClass(aClass));
      signature = SAM_SIGNATURE_LIGHT_CACHE_KEY.putCachedValue(aClass, newCached).get();
    }

    return signature != null ? substitute(signature, substitutor): null;
  }

  @Nullable
  private static MethodSignature doFindSingleAbstractMethodClass(@Nonnull PsiClass aClass) {
    Collection<MethodSignature> toImplement = OverrideImplementExploreUtil.getMethodSignaturesToImplement(aClass);
    if (toImplement.size() > 1) return null;

    MethodSignature abstractSignature = toImplement.isEmpty() ? null : toImplement.iterator().next();
    for (PsiMethod method : aClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        if (abstractSignature != null) return null;
        abstractSignature = method.getSignature(PsiSubstitutor.EMPTY);
      }
    }

    return abstractSignature;
  }

  @Nonnull
  private static MethodSignature substitute(@Nonnull MethodSignature signature, @Nonnull PsiSubstitutor substitutor) {
    return MethodSignatureUtil.createMethodSignature(signature.getName(), signature.getParameterTypes(), PsiTypeParameter.EMPTY_ARRAY, substitutor, false);
  }

  @Nullable
  public static MethodSignature findSAMSignature(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      if (TypesUtil.isClassType(type, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) return null;

      PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = result.getElement();

      if (aClass != null) {
        return findSingleAbstractMethod(aClass, result.getSubstitutor());
      }
    }

    return null;
  }
}
