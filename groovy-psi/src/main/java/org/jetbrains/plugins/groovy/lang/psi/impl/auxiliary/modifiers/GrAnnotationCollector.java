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
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

public class GrAnnotationCollector {

  @Nonnull
  public static GrAnnotation[] getResolvedAnnotations(@Nonnull GrModifierList modifierList) {
    GrAnnotation[] rawAnnotations = modifierList.getRawAnnotations();

    if (!hasAliases(rawAnnotations)) return rawAnnotations;

    List<GrAnnotation> result = ContainerUtil.newArrayList();
    for (GrAnnotation annotation : rawAnnotations) {
      PsiAnnotation annotationCollector = findAnnotationCollector(annotation);
      if (annotationCollector != null) {
        collectAnnotations(result, annotation, annotationCollector);
      }
      else {
        result.add(annotation);
      }
    }


    return result.toArray(new GrAnnotation[result.size()]);
  }

  private static boolean hasAliases(@Nonnull GrAnnotation[] rawAnnotations) {
    for (GrAnnotation annotation : rawAnnotations) {
      PsiAnnotation annotationCollector = findAnnotationCollector(annotation);
      if (annotationCollector != null) {
        return true;
      }
    }

    return false;
  }

  /**
   *
   * @param list resulting collection of aliased annotations
   * @param alias alias annotation
   * @param annotationCollector @AnnotationCollector annotation used in alias declaration
   * @return set of used arguments of alias annotation
   */
  @Nonnull
  public static Set<String> collectAnnotations(@Nonnull List<GrAnnotation> list,
                                               @Nonnull GrAnnotation alias,
                                               @Nonnull PsiAnnotation annotationCollector) {

    PsiModifierList modifierList = (PsiModifierList)annotationCollector.getParent();

    Map<String, Map<String, PsiNameValuePair>> annotations = new LinkedHashMap<>();
    collectAliasedAnnotationsFromAnnotationCollectorValueAttribute(annotationCollector, annotations);
    collectAliasedAnnotationsFromAnnotationCollectorAnnotations(modifierList, annotations);

    PsiManager manager = alias.getManager();
    GrAnnotationNameValuePair[] attributes = alias.getParameterList().getAttributes();

    Set<String> allUsedAttrs = new LinkedHashSet<>();
    for (Map.Entry<String, Map<String, PsiNameValuePair>> entry : annotations.entrySet()) {
      String qname = entry.getKey();
      PsiClass resolved = JavaPsiFacade.getInstance(alias.getProject()).findClass(qname, alias.getResolveScope());
      if (resolved == null) continue;

      GrLightAnnotation annotation = new GrLightAnnotation(manager, alias.getLanguage(), qname, modifierList);

      Set<String> usedAttrs = new LinkedHashSet<>();
      for (GrAnnotationNameValuePair attr : attributes) {
        String name = attr.getName() != null ? attr.getName() : "value";
        if (resolved.findMethodsByName(name, false).length > 0) {
          annotation.addAttribute(attr);
          allUsedAttrs.add(name);
          usedAttrs.add(name);
        }
      }


      Map<String, PsiNameValuePair> defaults = entry.getValue();
      for (Map.Entry<String, PsiNameValuePair> defa : defaults.entrySet()) {
        if (!usedAttrs.contains(defa.getKey())) {
          annotation.addAttribute(defa.getValue());
        }
      }


      list.add(annotation);
    }

    return allUsedAttrs;
  }

  private static void collectAliasedAnnotationsFromAnnotationCollectorAnnotations(@Nonnull PsiModifierList modifierList,
                                                                                  @Nonnull Map<String, Map<String, PsiNameValuePair>> annotations) {
    PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiClass &&
        GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_DYNAMIC.equals(((PsiClass)parent).getQualifiedName())) {
      Map<String, PsiNameValuePair> params = new LinkedHashMap<>();
      annotations.put(GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC, params);
      GrAnnotation annotation =
        GroovyPsiElementFactory.getInstance(modifierList.getProject()).createAnnotationFromText("@CompileStatic(TypeCheckingMode.SKIP)");
      params.put("value", annotation.getParameterList().getAttributes()[0]);
      return;
    }

    PsiAnnotation[] rawAnnotations =
      modifierList instanceof GrModifierList ? ((GrModifierList)modifierList).getRawAnnotations() : modifierList.getAnnotations();
    for (PsiAnnotation annotation : rawAnnotations) {
      String qname = annotation.getQualifiedName();

      if (qname == null || qname.equals(GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR)) continue;

      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair pair : attributes) {
        Map<String, PsiNameValuePair> map = annotations.get(qname);
        if (map == null) {
          map = new LinkedHashMap<>();
          annotations.put(qname, map);
        }

        map.put(pair.getName() != null ? pair.getName() : "value", pair);
      }
      if (attributes.length == 0 && !annotations.containsKey(qname)) {
        annotations.put(qname, new LinkedHashMap<>());
      }
    }

  }

  private static void collectAliasedAnnotationsFromAnnotationCollectorValueAttribute(@Nonnull PsiAnnotation annotationCollector,
                                                                                     @Nonnull Map<String, Map<String, PsiNameValuePair>> annotations) {
    PsiAnnotationMemberValue annotationsFromValue = annotationCollector.findAttributeValue("value");

    if (annotationsFromValue instanceof GrAnnotationArrayInitializer) {
      for (GrAnnotationMemberValue member : ((GrAnnotationArrayInitializer)annotationsFromValue).getInitializers()) {
        if (member instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression)member).resolve();
          if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
            annotations.put(((PsiClass)resolved).getQualifiedName(), new LinkedHashMap<>());
          }
        }
      }
    }
  }

  @Nullable
  public static PsiAnnotation findAnnotationCollector(@Nullable PsiClass clazz) {
    if (clazz != null) {
      PsiModifierList modifierList = clazz.getModifierList();
      if (modifierList != null) {
        PsiAnnotation[] annotations = modifierList instanceof GrModifierList ? ((GrModifierList)modifierList).getRawAnnotations() : modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
          if (GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(annotation.getQualifiedName())) {
            return annotation;
          }
        }
      }
    }

    return null;
  }


  @Nullable
  public static PsiAnnotation findAnnotationCollector(@Nonnull GrAnnotation annotation) {
    GrCodeReferenceElement ref = annotation.getClassReference();

    PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiClass) {
      return findAnnotationCollector((PsiClass)resolved);
    }
    else {
      return null;
    }
  }
}
