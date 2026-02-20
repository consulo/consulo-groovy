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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.application.util.function.Computable;
import consulo.component.messagebus.MessageBusConnection;
import consulo.ide.ServiceManager;
import consulo.language.psi.AnyPsiChangeListener;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.layer.event.ModuleRootAdapter;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Maps;
import consulo.util.lang.ref.SoftReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static com.intellij.java.language.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*;

/**
 * @author ven
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GroovyPsiManager {
  private static final Logger LOG = Logger.getInstance(GroovyPsiManager.class);
  private static final Set<String> ourPopularClasses = Set.of(GROOVY_LANG_CLOSURE,
                                                              DEFAULT_BASE_CLASS_NAME,
                                                              GROOVY_OBJECT_SUPPORT,
                                                              GROOVY_LANG_SCRIPT,
                                                              JAVA_UTIL_LIST,
                                                              JAVA_UTIL_COLLECTION,
                                                              JAVA_LANG_STRING);
  private final Project myProject;

  private volatile Map<String, GrTypeDefinition> myArrayClass = new HashMap<String, GrTypeDefinition>();

  private final ConcurrentMap<GroovyPsiElement, PsiType> myCalculatedTypes = ContainerUtil.createConcurrentWeakMap();
  private final ConcurrentMap<String, SoftReference<Map<GlobalSearchScope, PsiClass>>> myClassCache = ContainerUtil.newConcurrentMap();
  private final ConcurrentMap<PsiMember, Boolean> myCompileStatic = ContainerUtil.newConcurrentMap();

  private static final RecursionGuard<PsiElement> ourGuard = RecursionManager.createGuard("groovyPsiManager");

  @Inject
  public GroovyPsiManager(Project project) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(AnyPsiChangeListener.class, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        dropTypesCache();

        if (isPhysical) {
          myClassCache.clear();
        }
      }
    });

    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ModuleRootListener.class, new ModuleRootAdapter() {
      public void rootsChanged(ModuleRootEvent event) {
        dropTypesCache();
        myClassCache.clear();
      }
    });
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
    myCompileStatic.clear();
  }

  public static boolean isInheritorCached(@Nullable PsiClass aClass, @Nonnull String baseClassName) {
    if (aClass == null) return false;

    return InheritanceUtil.isInheritorOrSelf(aClass,
                                             getInstance(aClass.getProject()).findClassWithCache(baseClassName, aClass.getResolveScope()),
                                             true);
  }

  public static boolean isInheritorCached(@Nullable PsiType type, @Nonnull String baseClassName) {
    if (type instanceof PsiClassType) {
      return isInheritorCached(((PsiClassType)type).resolve(), baseClassName);
    }
    return false;
  }

  public static GroovyPsiManager getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiManager.class);
  }

  public PsiClassType createTypeByFQClassName(@Nonnull String fqName, @Nonnull GlobalSearchScope resolveScope) {
    if (ourPopularClasses.contains(fqName)) {
      PsiClass result = findClassWithCache(fqName, resolveScope);
      if (result != null) {
        return JavaPsiFacade.getElementFactory(myProject).createType(result);
      }
    }

    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqName, resolveScope);
  }

  public boolean isCompileStatic(@Nonnull PsiMember member) {
    Boolean aBoolean = myCompileStatic.get(member);
    if (aBoolean == null) {
      aBoolean = Maps.cacheOrGet(myCompileStatic, member, isCompileStaticInner(member));
    }
    return aBoolean;
  }

  private boolean isCompileStaticInner(@Nonnull PsiMember member) {
    PsiModifierList list = member.getModifierList();
    if (list != null) {
      PsiAnnotation compileStatic = list.findAnnotation(GROOVY_TRANSFORM_COMPILE_STATIC);
      if (compileStatic != null) return checkForPass(compileStatic);
      PsiAnnotation typeChecked = list.findAnnotation(GROOVY_TRANSFORM_TYPE_CHECKED);
      if (typeChecked != null) return checkForPass(typeChecked);
    }
    PsiClass aClass = member.getContainingClass();
    if (aClass != null) return isCompileStatic(aClass);
    return false;
  }

  private static boolean checkForPass(@Nonnull PsiAnnotation annotation) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
    return value == null ||
      value instanceof PsiReference &&
        ResolveUtil.isEnumConstant((PsiReference)value, "PASS", GROOVY_TRANSFORM_TYPE_CHECKING_MODE);
  }

  @Nullable
  public PsiClass findClassWithCache(@Nonnull String fqName, @Nonnull GlobalSearchScope resolveScope) {
    SoftReference<Map<GlobalSearchScope, PsiClass>> reference = myClassCache.get(fqName);
    Map<GlobalSearchScope, PsiClass> map = reference == null ? null : reference.get();
    if (map == null) {
      map = ContainerUtil.newConcurrentMap();
      myClassCache.put(fqName, new SoftReference<Map<GlobalSearchScope, PsiClass>>(map));
    }
    PsiClass cached = map.get(resolveScope);
    if (cached != null) {
      return cached;
    }

    PsiClass result = JavaPsiFacade.getInstance(myProject).findClass(fqName, resolveScope);
    if (result != null) {
      map.put(resolveScope, result);
    }
    return result;
  }


  private static final PsiType UNKNOWN_TYPE = new PsiPrimitiveType("unknown type", PsiAnnotation.EMPTY_ARRAY);

  @Nullable
  public <T extends GroovyPsiElement> PsiType getType(@Nonnull T element, @Nonnull Function<T, PsiType> calculator) {
    PsiType type = myCalculatedTypes.get(element);
    if (type == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      type = calculator.apply(element);
      if (type == null) {
        type = UNKNOWN_TYPE;
      }
      if (stamp.mayCacheNow()) {
        type = Maps.cacheOrGet(myCalculatedTypes, element, type);
      }
      else {
        PsiType alreadyInferred = myCalculatedTypes.get(element);
        if (alreadyInferred != null) {
          type = alreadyInferred;
        }
      }
    }
    if (!type.isValid()) {
      LOG.error("Type is invalid: " + type + "; element: " + element + " of class " + element.getClass());
    }
    return UNKNOWN_TYPE == type ? null : type;
  }

  @Nullable
  public GrTypeDefinition getArrayClass(@Nonnull PsiType type) {
    String typeText = type.getCanonicalText();
    GrTypeDefinition definition = myArrayClass.get(typeText);
    if (definition == null) {
      try {
        definition = GroovyPsiElementFactory.getInstance(myProject)
                                            .createTypeDefinition("class __ARRAY__ { public int length; public " + typeText + "[] clone(){} }");
        myArrayClass.put(typeText, definition);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    return definition;
  }

  @Nullable
  public static PsiType inferType(@Nonnull PsiElement element, @Nonnull Computable<PsiType> computable) {
    List<? extends PsiElement> stack = ourGuard.currentStack();
    if (stack.size() > 7) { //don't end up walking the whole project PSI
      ourGuard.prohibitResultCaching(stack.get(0));
      return null;
    }

    return ourGuard.doPreventingRecursion(element, true, computable);
  }

}
