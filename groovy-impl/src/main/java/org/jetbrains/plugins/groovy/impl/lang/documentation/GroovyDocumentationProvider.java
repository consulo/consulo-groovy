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
package org.jetbrains.plugins.groovy.impl.lang.documentation;

import com.intellij.java.impl.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.java.impl.lang.java.JavaDocumentationProvider;
import com.intellij.java.language.impl.codeInsight.javadoc.JavaDocUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.CodeDocumentationAwareCommenter;
import consulo.language.Commenter;
import consulo.language.Language;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.action.CodeDocumentationUtil;
import consulo.language.editor.documentation.CodeDocumentationProvider;
import consulo.language.editor.documentation.CompositeDocumentationProvider;
import consulo.language.editor.documentation.ExternalDocumentationProvider;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.dsl.CustomMembersGenerator;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.impl.lang.completion.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrImplicitVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
@ExtensionImpl
public class GroovyDocumentationProvider implements CodeDocumentationProvider, ExternalDocumentationProvider, LanguageDocumentationProvider {
  private static final String LINE_SEPARATOR = "\n";

  @NonNls
  private static final String RETURN_TAG = "@return";
  @NonNls
  private static final String THROWS_TAG = "@throws";
  private static final String BODY_HTML = "</body></html>";

  private static PsiSubstitutor calcSubstitutor(PsiElement originalElement) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (originalElement instanceof GrReferenceExpression) {
      substitutor = ((GrReferenceExpression)originalElement).advancedResolve().getSubstitutor();
    }
    return substitutor;
  }


  @Override
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrVariable || element instanceof GrImplicitVariable) {
      StringBuilder buffer = new StringBuilder();
      PsiVariable variable = (PsiVariable)element;

      if (originalElement instanceof GrVariableDeclaration && ((GrVariableDeclaration)originalElement)
        .getVariables().length > 1) {
        for (GrVariable var : ((GrVariableDeclaration)originalElement).getVariables()) {
          generateVariableInfo(originalElement, buffer, var);
          buffer.append("\n\n");
        }
      }
      else {
        generateVariableInfo(originalElement, buffer, variable);
      }
      return buffer.toString();
    }
    else if (element instanceof PsiMethod) {
      StringBuilder buffer = new StringBuilder();
      PsiMethod method = (PsiMethod)element;
      if (method instanceof GrGdkMethod) {
        buffer.append("[GDK] ");
      }
      else {
        PsiClass hisClass = method.getContainingClass();
        if (hisClass != null) {
          String qName = hisClass.getQualifiedName();
          if (qName != null) {
            buffer.append(qName).append("\n");
          }
        }
      }

      PsiSubstitutor substitutor = calcSubstitutor(originalElement);
      if (!method.isConstructor()) {
        final PsiType substituted = substitutor.substitute(PsiUtil.getSmartReturnType(method));
        appendTypeString0(buffer, substituted, originalElement);
        buffer.append(" ");
      }
      buffer.append(method.getName()).append(" ");
      buffer.append("(");
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i > 0) {
          buffer.append(", ");
        }
        if (parameter instanceof GrParameter) {
          GroovyPresentationUtil.appendParameterPresentation((GrParameter)parameter, substitutor,
                                                             TypePresentation.LINK, buffer);
        }
        else {
          PsiType type = parameter.getType();
          appendTypeString0(buffer, substitutor.substitute(type), originalElement);
          buffer.append(" ");
          buffer.append(parameter.getName());
        }
      }
      buffer.append(")");
      final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
      if (referencedTypes.length > 0) {
        buffer.append("\nthrows ");
        for (PsiClassType referencedType : referencedTypes) {
          appendTypeString0(buffer, referencedType, originalElement);
          buffer.append(", ");
        }
        buffer.delete(buffer.length() - 2, buffer.length());
      }
      return buffer.toString();
    }
    else if (element instanceof GrTypeDefinition) {
      return generateClassInfo((GrTypeDefinition)element);
    }

    return null;
  }

  private static void generateVariableInfo(PsiElement originalElement, StringBuilder buffer, PsiVariable variable) {
    if (variable instanceof PsiField) {
      final PsiClass parentClass = ((PsiField)variable).getContainingClass();
      if (parentClass != null) {
        buffer.append(JavaDocUtil.getShortestClassName(parentClass, variable));
        newLine(buffer);
      }
      generateModifiers(buffer, variable);
    }
    final PsiType type = variable instanceof GrVariable ? ((GrVariable)variable).getDeclaredType() : variable
      .getType();
    appendTypeString0(buffer, calcSubstitutor(originalElement).substitute(type), originalElement);
    buffer.append(" ");
    buffer.append(variable.getName());

    if (variable instanceof GrVariable) {
      newLine(buffer);

      while (originalElement != null) {
        PsiReference ref = originalElement.getReference();
        if (ref != null && ref.resolve() != null) {
          break;
        }

        originalElement = originalElement.getParent();
      }

      if (originalElement != null) {
        appendInferredType(originalElement, (GrVariable)variable, buffer);
      }
    }
  }

  private static void appendInferredType(PsiElement originalElement, GrVariable variable, StringBuilder buffer) {
    PsiType inferredType = null;
    if (PsiImplUtil.isWhiteSpaceOrNls(originalElement)) {
      originalElement = PsiTreeUtil.prevLeaf(originalElement);
    }
    if (originalElement != null && originalElement.getNode().getElementType() == GroovyTokenTypes.mIDENT) {
      originalElement = originalElement.getParent();
    }
    if (originalElement instanceof GrReferenceExpression) {
      inferredType = ((GrReferenceExpression)originalElement).getType();
    }
    else if (originalElement instanceof GrVariableDeclaration) {
      inferredType = variable.getTypeGroovy();
    }
    else if (originalElement instanceof GrVariable) {
      inferredType = ((GrVariable)originalElement).getTypeGroovy();
    }

    if (inferredType != null) {
      buffer.append("[inferred type] ");
      appendTypeString0(buffer, inferredType, originalElement);
    }
    else {
      buffer.append("[cannot infer type]");
    }
  }

  public static void appendTypeString0(StringBuilder buffer, final PsiType type, PsiElement context) {
    if (type != null) {
      JavaDocInfoGenerator.generateType(buffer, type, context);
    }
    else {
      buffer.append(GrModifier.DEF);
    }
  }

  private static void generateModifiers(StringBuilder buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);

    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static void newLine(StringBuilder buffer) {
    buffer.append(LINE_SEPARATOR);
  }

  private static String generateClassInfo(PsiClass aClass) {
    StringBuilder buffer = new StringBuilder();
    GroovyFile file = (GroovyFile)aClass.getContainingFile();

    String packageName = file.getPackageName();
    if (!packageName.isEmpty()) {
      buffer.append(packageName).append("\n");
    }

    final String classString = aClass.isInterface() ? "interface" : aClass instanceof PsiTypeParameter ? "type " +
      "parameter" : aClass.isEnum() ? "enum" : "class";
    buffer.append(classString).append(" ").append(aClass.getName());

    JavaDocumentationProvider.generateTypeParameters(aClass, buffer);

    JavaDocumentationProvider.writeExtends(aClass, buffer, aClass.getExtendsListTypes());
    JavaDocumentationProvider.writeImplements(aClass, buffer, aClass.getImplementsListTypes());

    return buffer.toString();
  }

  public static void appendTypeString(@Nonnull StringBuilder buffer, @Nullable PsiType type, PsiElement context) {
    if (type instanceof GrTraitType) {
      generateTraitType(buffer, ((GrTraitType)type), context);
    }
    else if (type != null) {
      JavaDocInfoGenerator.generateType(buffer, type, context);
    }
    else {
      buffer.append(GrModifier.DEF);
    }
  }

  private static void generateTraitType(@Nonnull StringBuilder buffer, @Nonnull GrTraitType type, PsiElement context) {
    PsiClassType exprType = type.getExprType();
    List<PsiClassType> traitTypes = type.getTraitTypes();
    appendTypeString(buffer, exprType, context);
    buffer.append(" as ");
    for (PsiClassType traitType : traitTypes) {
      appendTypeString(buffer, traitType, context);
      buffer.append(", ");
    }
    buffer.delete(buffer.length() - 2, buffer.length());
  }

  @Override
  @Nullable
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    List<String> result = new ArrayList<String>();
    PsiElement docElement = getDocumentationElement(element, originalElement);
    if (docElement != null) {
      ContainerUtil.addIfNotNull(result, docElement.getUserData(NonCodeMembersHolder.DOCUMENTATION_URL));
    }
    List<String> list = JavaDocumentationProvider.getExternalJavaDocUrl(element);
    if (list != null) {
      result.addAll(list);
    }
    return result.isEmpty() ? null : result;
  }

  @Override
  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (element instanceof CustomMembersGenerator.GdslNamedParameter) {
      CustomMembersGenerator.GdslNamedParameter parameter = (CustomMembersGenerator.GdslNamedParameter)element;
      String result = "<pre><b>" + parameter.getName() + "</b>";
      if (parameter.myParameterTypeText != null) {
        result += ": " + parameter.myParameterTypeText;
      }
      result += "</pre>";
      if (parameter.docString != null) {
        result += "<p>" + parameter.docString;
      }
      return result;
    }

    if (element instanceof GrReferenceExpression) {
      return getMethodCandidateInfo((GrReferenceExpression)element);
    }

    element = getDocumentationElement(element, originalElement);

    if (element == null) {
      return null;
    }

    String standard = element.getNavigationElement() instanceof PsiDocCommentOwner ? JavaDocumentationProvider
      .generateExternalJavadoc(element) : null;

    if (element instanceof GrVariable &&
      ((GrVariable)element).getTypeElementGroovy() == null &&
      standard != null) {
      final String truncated = StringUtil.trimEnd(standard, BODY_HTML);

      StringBuilder buffer = new StringBuilder(truncated);
      buffer.append("<p>");
      if (originalElement != null) {
        appendInferredType(originalElement, (GrVariable)element, buffer);
      }
      else if (element.getParent() instanceof GrVariableDeclaration) {
        appendInferredType(element.getParent(), (GrVariable)element, buffer);
      }

      if (!truncated.equals(standard)) {
        buffer.append(BODY_HTML);
      }
      standard = buffer.toString();
    }

    String gdslDoc = element.getUserData(NonCodeMembersHolder.DOCUMENTATION);
    if (gdslDoc != null) {
      if (standard != null) {
        String truncated = StringUtil.trimEnd(standard, BODY_HTML);
        String appended = truncated + "<p>" + gdslDoc;
        if (truncated.equals(standard)) {
          return appended;
        }
        return appended + BODY_HTML;
      }
      return gdslDoc;
    }

    return standard;
  }

  private static PsiElement getDocumentationElement(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrGdkMethod) {
      element = ((GrGdkMethod)element).getStaticMethod();
    }

    final GrDocComment doc = PsiTreeUtil.getParentOfType(originalElement, GrDocComment.class);
    if (doc != null) {
      element = GrDocCommentUtil.findDocOwner(doc);
    }

    if (element instanceof GrLightVariable) {
      PsiElement navigationElement = element.getNavigationElement();

      if (navigationElement != null) {
        element = navigationElement;

        if (element.getContainingFile() instanceof PsiCompiledFile) {
          navigationElement = element.getNavigationElement();
          if (navigationElement != null) {
            element = navigationElement;
          }
        }

        if (element instanceof GrAccessorMethod) {
          element = ((GrAccessorMethod)element).getProperty();
        }
      }
    }

    if (element instanceof GrPropertyForCompletion) {
      element = ((GrPropertyForCompletion)element).getOriginalAccessor();
    }
    return element;
  }

  @Override
  public String fetchExternalDocumentation(final Project project, PsiElement element, final List<String> docUrls) {
    return JavaDocumentationProvider.fetchExternalJavadoc(element, project, docUrls);
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement);
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {
  }

  private static String getMethodCandidateInfo(GrReferenceExpression expr) {
    final GroovyResolveResult[] candidates = expr.multiResolve(false);
    final String text = expr.getText();
    if (candidates.length > 0) {
      @NonNls final StringBuilder sb = new StringBuilder();
      for (final GroovyResolveResult candidate : candidates) {
        final PsiElement element = candidate.getElement();
        if (!(element instanceof PsiMethod)) {
          continue;
        }
        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(),
                                                      PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                      PsiFormatUtilBase.SHOW_TYPE);
        createElementLink(sb, element, str);
      }
      return CodeInsightBundle.message("javadoc.candidates", text, sb);
    }
    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private static void createElementLink(@NonNls final StringBuilder sb, final PsiElement element, final String str) {
    sb.append("&nbsp;&nbsp;<a href=\"psi_element://");
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }

  @Override
  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (object instanceof GroovyResolveResult) {
      return ((GroovyResolveResult)object).getElement();
    }
    if (object instanceof NamedArgumentDescriptor) {
      return ((NamedArgumentDescriptor)object).getNavigationElement();
    }
    return null;
  }

  @Override
  @Nullable
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  @Override
  public PsiComment findExistingDocComment(PsiComment contextElement) {
    if (contextElement instanceof GrDocComment) {
      final GrDocCommentOwner owner = GrDocCommentUtil.findDocOwner((GrDocComment)contextElement);
      if (owner != null) {
        return owner.getDocComment();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Pair<PsiElement, PsiComment> parseContext(@Nonnull PsiElement startPoint) {
    for (PsiElement e = startPoint; e != null; e = e.getParent()) {
      if (e instanceof GrDocCommentOwner) {
        return Pair.<PsiElement, PsiComment>create(e, ((GrDocCommentOwner)e).getDocComment());
      }
    }
    return null;
  }

  @Override
  public String generateDocumentationContentStub(PsiComment contextComment) {
    if (!(contextComment instanceof GrDocComment)) {
      return null;
    }

    final GrDocCommentOwner owner = GrDocCommentUtil.findDocOwner((GrDocComment)contextComment);
    if (owner == null) {
      return null;
    }

    Project project = contextComment.getProject();
    final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)Commenter.forLanguage(owner.getLanguage());


    StringBuilder builder = new StringBuilder();
    if (owner instanceof GrMethod) {
      final GrMethod method = (GrMethod)owner;
      JavaDocumentationProvider.generateParametersTakingDocFromSuperMethods(project, builder, commenter,
                                                                            method);

      final PsiType returnType = method.getInferredReturnType();
      if ((returnType != null || method.getModifierList().hasModifierProperty(GrModifier.DEF)) && returnType
        != PsiType.VOID) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(RETURN_TAG,
                                                                  project,
                                                                  commenter));
        builder.append(LINE_SEPARATOR);
      }

      final PsiClassType[] references = method.getThrowsList().getReferencedTypes();
      for (PsiClassType reference : references) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(THROWS_TAG,
                                                                  project,
                                                                  commenter));
        builder.append(reference.getClassName());
        builder.append(LINE_SEPARATOR);
      }
    }
    else if (owner instanceof GrTypeDefinition) {
      final PsiTypeParameterList typeParameterList = ((PsiClass)owner).getTypeParameterList();
      if (typeParameterList != null) {
        JavaDocumentationProvider.createTypeParamsListComment(builder, project, commenter, typeParameterList);
      }
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
