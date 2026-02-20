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

package org.jetbrains.plugins.groovy.impl.lang.resolve.ast;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*;

/**
 * @author peter
 */
@ExtensionImpl
public class ConstructorAnnotationsProcessor extends AstTransformContributor {

  @Override
  public void collectMethods(@Nonnull GrTypeDefinition typeDefinition, @Nonnull Collection<PsiMethod> collector) {
    if (typeDefinition.getName() == null) return;

    PsiModifierList modifierList = typeDefinition.getModifierList();
    if (modifierList == null) return;

    PsiAnnotation tupleConstructor = modifierList.findAnnotation(GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR);
    boolean immutable = modifierList.findAnnotation(GROOVY_LANG_IMMUTABLE) != null ||
                              modifierList.findAnnotation(GROOVY_TRANSFORM_IMMUTABLE) != null;
    boolean canonical = modifierList.findAnnotation(GROOVY_TRANSFORM_CANONICAL) != null;
    if (!immutable && !canonical && tupleConstructor == null) {
      return;
    }

    if (tupleConstructor != null &&
        typeDefinition.getCodeConstructors().length > 0 &&
        !PsiUtil.getAnnoAttributeValue(tupleConstructor, "force", false)) {
      return;
    }

    GrLightMethodBuilder fieldsConstructor = generateFieldConstructor(typeDefinition, tupleConstructor, immutable, canonical);
    GrLightMethodBuilder mapConstructor = generateMapConstructor(typeDefinition);

    collector.add(fieldsConstructor);
    collector.add(mapConstructor);
  }

  @Nonnull
  private static GrLightMethodBuilder generateMapConstructor(@Nonnull GrTypeDefinition typeDefinition) {
    GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    mapConstructor.addParameter("args", CommonClassNames.JAVA_UTIL_HASH_MAP, false);
    mapConstructor.setConstructor(true);
    mapConstructor.setContainingClass(typeDefinition);
    return mapConstructor;
  }

  @Nonnull
  private static GrLightMethodBuilder generateFieldConstructor(@Nonnull GrTypeDefinition typeDefinition,
                                                               @Nullable PsiAnnotation tupleConstructor,
                                                               boolean immutable,
                                                               boolean canonical) {
    GrLightMethodBuilder fieldsConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    fieldsConstructor.setConstructor(true);
    fieldsConstructor.setNavigationElement(typeDefinition);
    fieldsConstructor.setContainingClass(typeDefinition);

    Set<String> excludes = new HashSet<String>();
    if (tupleConstructor != null) {
      for (String s : PsiUtil.getAnnoAttributeValue(tupleConstructor, "excludes", "").split(",")) {
        String name = s.trim();
        if (StringUtil.isNotEmpty(name)) {
          excludes.add(name);
        }
      }
    }


    if (tupleConstructor != null) {
      boolean superFields = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperFields", false);
      boolean superProperties = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperProperties", false);
      if (superFields || superProperties) {
        addParametersForSuper(typeDefinition, fieldsConstructor, superFields, superProperties, new HashSet<PsiClass>(), excludes);
      }
    }

    addParameters(typeDefinition, fieldsConstructor,
                  tupleConstructor == null || PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeProperties", true),
                  tupleConstructor != null ? PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeFields", false) : !canonical,
                  !immutable, excludes);

    if (immutable) {
      fieldsConstructor.setOriginInfo("created by @Immutable");
    }
    else if (tupleConstructor != null) {
      fieldsConstructor.setOriginInfo("created by @TupleConstructor");
    }
    else /*if (canonical != null)*/ {
      fieldsConstructor.setOriginInfo("created by @Canonical");
    }
    return fieldsConstructor;
  }

  private static void addParametersForSuper(@Nonnull PsiClass typeDefinition,
                                            GrLightMethodBuilder fieldsConstructor,
                                            boolean superFields,
                                            boolean superProperties, Set<PsiClass> visited, Set<String> excludes) {
    PsiClass parent = typeDefinition.getSuperClass();
    if (parent != null && visited.add(parent) && !GROOVY_OBJECT_SUPPORT.equals(parent.getQualifiedName())) {
      addParametersForSuper(parent, fieldsConstructor, superFields, superProperties, visited, excludes);
      addParameters(parent, fieldsConstructor, superProperties, superFields, true, excludes);
    }
  }

  private static void addParameters(@Nonnull PsiClass psiClass,
                                    @Nonnull GrLightMethodBuilder fieldsConstructor,
                                    boolean includeProperties,
                                    boolean includeFields,
                                    boolean optional,
                                    @Nonnull Set<String> excludes) {

    PsiMethod[] methods = CollectClassMembersUtil.getMethods(psiClass, false);
    if (includeProperties) {
      for (PsiMethod method : methods) {
        if (!method.hasModifierProperty(PsiModifier.STATIC) && PropertyUtil.isSimplePropertySetter(method)) {
          String name = PropertyUtil.getPropertyNameBySetter(method);
          if (!excludes.contains(name)) {
            PsiType type = PropertyUtil.getPropertyType(method);
            assert type != null : method;
            fieldsConstructor.addParameter(new GrLightParameter(name, type, fieldsConstructor).setOptional(optional));
          }
        }
      }
    }

    Map<String,PsiMethod> properties = PropertyUtil.getAllProperties(true, false, methods);
    for (PsiField field : CollectClassMembersUtil.getFields(psiClass, false)) {
      String name = field.getName();
      if (includeFields ||
          includeProperties && field instanceof GrField && ((GrField)field).isProperty()) {
        if (!excludes.contains(name) && !field.hasModifierProperty(PsiModifier.STATIC) && !properties.containsKey(name)) {
          fieldsConstructor.addParameter(new GrLightParameter(name, field.getType(), fieldsConstructor).setOptional(optional));
        }
      }
    }
  }
}
