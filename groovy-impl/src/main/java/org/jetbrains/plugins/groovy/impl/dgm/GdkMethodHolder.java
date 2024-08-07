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
package org.jetbrains.plugins.groovy.impl.dgm;

import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.util.*;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author Max Medvedev
 */
public class GdkMethodHolder {
  private static Key<CachedValue<GdkMethodHolder>> CACHED_NON_STATIC = Key.create("Cached instance gdk method holder");
  private static Key<CachedValue<GdkMethodHolder>> CACHED_STATIC = Key.create("Cached static gdk method holder");

  private final Map<String, MultiMap<String, PsiMethod>> myOriginalMethodsByNameAndType;
  private final NotNullLazyValue<MultiMap<String, PsiMethod>> myOriginalMethodByType;
  private final boolean myStatic;
  private final GlobalSearchScope myScope;
  private final PsiManager myPsiManager;

  private GdkMethodHolder(final PsiClass categoryClass, final boolean isStatic, final GlobalSearchScope scope) {
    myStatic = isStatic;
    myScope = scope;
    final MultiMap<String, PsiMethod> byName = new MultiMap<>();
    myPsiManager = categoryClass.getManager();
    for (PsiMethod m : categoryClass.getMethods()) {
      final PsiParameter[] params = m.getParameterList().getParameters();
      if (params.length == 0) {
        continue;
      }

      byName.putValue(m.getName(), m);
    }
    this.myOriginalMethodByType = new VolatileNotNullLazyValue<MultiMap<String, PsiMethod>>() {
      @Nonnull
      @Override
      protected MultiMap<String, PsiMethod> compute() {
        MultiMap<String, PsiMethod> map = new MultiMap<>();
        for (PsiMethod method : byName.values()) {
          if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            continue;
          }
          map.putValue(getCategoryTargetType(method).getCanonicalText(), method);
        }
        return map;
      }
    };

    myOriginalMethodsByNameAndType = ConcurrentFactoryMap.createMap(name -> {
      MultiMap<String, PsiMethod> map = new MultiMap<>();
      for (PsiMethod method : byName.get(name)) {
        map.putValue(getCategoryTargetType(method).getCanonicalText(), method);
      }
      return map;
    });
  }

  private PsiType getCategoryTargetType(PsiMethod method) {
    final PsiType parameterType = method.getParameterList().getParameters()[0].getType();
    return TypesUtil.boxPrimitiveType(TypeConversionUtil.erasure(parameterType), myPsiManager, myScope);
  }

  public boolean processMethods(PsiScopeProcessor processor, ResolveState state, PsiType qualifierType, Project project) {
    if (qualifierType == null) {
      return true;
    }

    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);
    final MultiMap<String, PsiMethod> map = name != null ? myOriginalMethodsByNameAndType.get(name) : myOriginalMethodByType.getValue();
    if (map.isEmpty()) {
      return true;
    }

    for (String superType : ResolveUtil.getAllSuperTypes(qualifierType, project).keySet()) {
      for (PsiMethod method : map.get(superType)) {
        String info = GdkMethodUtil.generateOriginInfo(method);
        GrGdkMethod gdk = GrGdkMethodImpl.createGdkMethod(method, myStatic, info);
        if (!processor.execute(gdk, state)) {
          return false;
        }
      }
    }

    return true;
  }

  public static GdkMethodHolder getHolderForClass(final PsiClass categoryClass, final boolean isStatic, final GlobalSearchScope scope) {
    final Project project = categoryClass.getProject();
    Key<CachedValue<GdkMethodHolder>> key = isStatic ? CACHED_STATIC : CACHED_NON_STATIC;
    return CachedValuesManager.getManager(project).getCachedValue(categoryClass, key, new CachedValueProvider<GdkMethodHolder>() {
      @Override
      public Result<GdkMethodHolder> compute() {
        GdkMethodHolder result = new GdkMethodHolder(categoryClass, isStatic, scope);

        final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
        final VirtualFile vfile = categoryClass.getContainingFile().getVirtualFile();
        if (vfile != null && (rootManager.getFileIndex().isInLibraryClasses(vfile) || rootManager.getFileIndex()
                                                                                                 .isInLibrarySource(vfile))) {
          return Result.create(result, rootManager);
        }

        return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, rootManager);
      }
    }, false);
  }
}
