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

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.psi.PsiCompiledElement;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyChangeContextUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.ModifierListGenerator;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil");
  private static final String JAVA_LANG_OVERRIDE = "java.lang.Override";

  private static final String[] GROOVY_MODIFIERS = new String[]{
    //PsiModifier.PUBLIC,
    PsiModifier.PROTECTED,
    PsiModifier.PRIVATE,
    PsiModifier.STATIC,
    //PsiModifier.ABSTRACT,
    PsiModifier.FINAL,
    PsiModifier.SYNCHRONIZED,
  };

  private GroovyOverrideImplementUtil() {
  }

  public static GrMethod generateMethodPrototype(GrTypeDefinition aClass,
                                                 PsiMethod method,
                                                 PsiSubstitutor substitutor) {
    final Project project = aClass.getProject();
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    String templName = isAbstract ? JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
    final GrMethod result = createOverrideImplementMethodSignature(project, method, substitutor, aClass);

    setupModifierList(result);
    setupOverridingMethodBody(project, method, result, template, substitutor);

    setupAnnotations(aClass, method, result);

    GroovyChangeContextUtil.encodeContextInfo(result);
    return result;
  }

  private static void setupAnnotations(@Nonnull GrTypeDefinition aClass, @Nonnull PsiMethod method, @Nonnull GrMethod result) {
    if (OverrideImplementUtil.isInsertOverride(method, aClass)) {
      result.getModifierList().addAnnotation(JAVA_LANG_OVERRIDE);
    }

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

    final PsiParameter[] originalParams = method.getParameterList().getParameters();

    GrParameter[] parameters = result.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      final PsiParameter original = originalParams[i];

      for (PsiAnnotation annotation : original.getModifierList().getAnnotations()) {
        final GrModifierList modifierList = parameter.getModifierList();
        if (annotation instanceof GrAnnotation) {
          modifierList.add(annotation);
        }
        else {
          modifierList.add(factory.createAnnotationFromText(annotation.getText()));
        }
      }
    }
  }

  private static void setupModifierList(GrMethod result) {
    PsiModifierList modifierList = result.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    modifierList.setModifierProperty(PsiModifier.NATIVE, false);
  }


  @Nonnull
  private static GrMethod createOverrideImplementMethodSignature(@Nonnull Project project,
                                                                 @Nonnull PsiMethod superMethod,
                                                                 @Nonnull PsiSubstitutor substitutor,
                                                                 @Nonnull PsiClass aClass) {
    StringBuilder buffer = new StringBuilder();
    final boolean hasModifiers = ModifierListGenerator.writeModifiers(buffer, superMethod.getModifierList(), GROOVY_MODIFIERS, false);

    final PsiTypeParameter[] superTypeParameters = superMethod.getTypeParameters();
    final List<PsiTypeParameter> typeParameters = new ArrayList<PsiTypeParameter>();
    final Map<PsiTypeParameter, PsiType> map = substitutor.getSubstitutionMap();
    for (PsiTypeParameter parameter : superTypeParameters) {
      if (!map.containsKey(parameter)) {
        typeParameters.add(parameter);
      }
    }

    final PsiType returnType = substitutor.substitute(getSuperReturnType(superMethod));

    boolean isConstructor = superMethod.isConstructor();
    if (!isConstructor && (!hasModifiers && returnType == null || typeParameters.size() > 0)) {
      buffer.append("def ");
    }

    if (typeParameters.size() > 0) {
      LOG.assertTrue(!isConstructor);
      buffer.append('<');
      for (PsiTypeParameter parameter : typeParameters) {
        buffer.append(parameter.getText());
        buffer.append(", ");
      }
      buffer.replace(buffer.length() - 2, buffer.length(), ">");
    }

    final String name;
    if (isConstructor) {
      name = aClass.getName();
    }
    else {
      if (returnType != null) {
        buffer.append(returnType.getCanonicalText()).append(" ");
      }

      name = superMethod.getName();
    }
    buffer.append(name);

    buffer.append("(");
    final PsiParameter[] parameters = superMethod.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) buffer.append(", ");
      PsiParameter parameter = parameters[i];



      if (!(parameter instanceof GrParameter && ((GrParameter)parameter).getTypeElementGroovy() == null)) {
        final PsiType parameterType = substitutor.substitute(parameter.getType());
        buffer.append(parameterType.getCanonicalText());
        buffer.append(" ");
      }
      final String paramName = parameter.getName();
      if (paramName != null) {
        buffer.append(paramName);
      }
      else if (parameter instanceof PsiCompiledElement) {
        buffer.append(((PsiParameter)((PsiCompiledElement)parameter).getMirror()).getName());
      }
    }

    buffer.append(") ");
    final PsiReferenceList list = superMethod.getThrowsList();
    final PsiClassType[] types = list.getReferencedTypes();
    if (types.length > 0) {
      buffer.append("throws ");
      for (PsiClassType type : types) {
        buffer.append(type.getCanonicalText());
        buffer.append(" ,");
      }

      buffer.delete(buffer.length() - 2, buffer.length());
    }
    buffer.append("{}");

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    if (isConstructor) {
      return factory.createConstructorFromText(name, buffer.toString(), superMethod);
    }
    else {
      return factory.createMethodFromText(buffer.toString(), superMethod);
    }
  }

  @Nullable
  private static PsiType getSuperReturnType(@Nonnull PsiMethod superMethod) {
    if (superMethod instanceof GrMethod) {
      final GrTypeElement element = ((GrMethod)superMethod).getReturnTypeElementGroovy();
      return element != null ? element.getType() : null;
    }

    return superMethod.getReturnType();
  }

  private static void setupOverridingMethodBody(Project project,
                                                PsiMethod method,
                                                GrMethod resultMethod,
                                                FileTemplate template,
                                                PsiSubstitutor substitutor) {
    final PsiType returnType = substitutor.substitute(getSuperReturnType(method));

    String returnTypeText = "";
    if (returnType != null) {
      returnTypeText = returnType.getPresentableText();
    }
    Properties properties = FileTemplateManager.getInstance().getDefaultProperties(project);

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnTypeText);
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(method, resultMethod));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, method.getContainingClass(), resultMethod);

    try {
      String bodyText = StringUtil.replace(template.getText(properties), ";", "");
      final GrCodeBlock newBody = GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n " + bodyText + "\n");

      resultMethod.setBlock(newBody);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nonnull
  private static String callSuper(PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (!superMethod.isConstructor() && superMethod.getReturnType() != PsiType.VOID) {
      buffer.append("return ");
    }
    buffer.append("super");
    PsiParameter[] parms = overriding.getParameterList().getParameters();
    if (!superMethod.isConstructor()) {
      buffer.append(".");
      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    for (int i = 0; i < parms.length; i++) {
      String name = parms[i].getName();
      if (i > 0) buffer.append(",");
      buffer.append(name);
    }
    buffer.append(")");
    return buffer.toString();
  }
}
