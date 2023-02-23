/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.light.LightFieldBuilder;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.annotation.Nonnull;

import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_COLLECTION;

/**
 * @author Maxim.Medvedev
 */
public class GrCollectionTypeMembersProvider extends NonCodeMembersContributor {

  @Override
  public String getParentClassName() {
    return JAVA_UTIL_COLLECTION;
  }

  @Override
  public void processDynamicElements(final @Nonnull PsiType qualifierType,
                                     PsiClass aClass,
                                     final PsiScopeProcessor processor,
                                     final PsiElement place,
                                     final ResolveState state) {
    final PsiType collectionType = PsiUtil.extractIterableTypeParameter(qualifierType, true);
    if (collectionType == null) return;

    PsiClass collectionClass = JavaPsiFacade.getInstance(place.getProject()).findClass(JAVA_UTIL_COLLECTION, place.getResolveScope());
    final PsiScopeProcessor fieldSearcher = new FieldSearcher(processor, collectionClass);
    ResolveUtil.processAllDeclarations(collectionType, fieldSearcher, state, place);
  }

  private static class FieldSearcher extends DelegatingScopeProcessor implements ClassHint, ElementClassHint {
    final PsiClass myCollectionClass;

    public FieldSearcher(PsiScopeProcessor processor, PsiClass collectionClass) {
      super(processor);
      myCollectionClass = collectionClass;
    }

    @Override
    public boolean execute(@Nonnull PsiElement element, ResolveState state) {
      if (element instanceof PsiField) {
        final PsiType type = ((PsiField)element).getType();
        final String typeText =
          type instanceof PsiClassType ? JAVA_UTIL_COLLECTION + "<" + type.getCanonicalText() + ">" : JAVA_UTIL_COLLECTION;
        LightFieldBuilder lightField = new LightFieldBuilder(((PsiField)element).getName(), typeText, element);
        lightField.setContainingClass(myCollectionClass);
        lightField.setOriginInfo("spread collection field");
        return super.execute(lightField, state);
      }
      return true;
    }

    @Override
    public <T> T getHint(@Nonnull Key<T> hintKey) {
      if (hintKey == NameHint.KEY) return super.getHint(hintKey);
      if (hintKey == ClassHint.KEY || hintKey == ElementClassHint.KEY) return (T)this;
      return null;
    }

    @Override
    public boolean shouldProcess(ResolveKind resolveKind) {
      return resolveKind == ResolveKind.PROPERTY;
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
      return kind == DeclarationKind.FIELD;
    }
  }
}
