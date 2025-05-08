/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInsight;

import com.intellij.java.impl.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.java.impl.codeInsight.daemon.impl.MarkerType;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.CodeInsightColors;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.codeEditor.markup.SeparatorPlacement;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.FunctionUtil;
import consulo.language.Language;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;

/**
 * @author ilyas
 * Same logic as for Java LMP
 */
@ExtensionImpl
public class GroovyLineMarkerProvider extends JavaLineMarkerProvider {
  @Inject
  public GroovyLineMarkerProvider(Application application, DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    super(application, daemonSettings, colorsManager);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  @Override
  public String getName() {
    return "Groovy line markers";
  }

  @Override
  @RequiredReadAction
  public LineMarkerInfo getLineMarkerInfo(@Nonnull final PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiNameIdentifierOwner) {
      if (parent instanceof GrField && element == ((GrField)parent).getNameIdentifierGroovy()) {
        for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors((GrField)parent)) {
          MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
          if (superSignature != null) {
            PsiMethod superMethod = superSignature.getMethod();
            boolean overrides =
              method.hasModifierProperty(PsiModifier.ABSTRACT) == superMethod.hasModifierProperty(PsiModifier.ABSTRACT) || superMethod.getBody() != null && GrTraitUtil
                .isTrait(superMethod.getContainingClass());
            final Image icon = overrides ? AllIcons.Gutter.OverridingMethod : AllIcons.Gutter.ImplementingMethod;
            final MarkerType type = GroovyMarkerTypes.OVERRIDING_PROPERTY_TYPE;
            return new LineMarkerInfo<>(element,
                                        element.getTextRange(),
                                        icon,
                                        Pass.LINE_MARKERS,
                                        type.getTooltip(),
                                        type.getNavigationHandler(),
                                        GutterIconRenderer.Alignment.LEFT);
          }
        }
      }
      else if (parent instanceof GrMethod &&
        element == ((GrMethod)parent).getNameIdentifierGroovy() &&
        hasSuperMethods((GrMethod)element.getParent())) {
        final Image icon = AllIcons.Gutter.OverridingMethod;
        final MarkerType type = GroovyMarkerTypes.GR_OVERRIDING_METHOD;
        return new LineMarkerInfo<>(element,
                                    element.getTextRange(),
                                    icon,
                                    Pass.LINE_MARKERS,
                                    type.getTooltip(),
                                    type.getNavigationHandler(),
                                    GutterIconRenderer.Alignment.LEFT);
      }
    }
    //need to draw method separator above docComment
    if (myDaemonSettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember || element1 instanceof GrVariableDeclarationImpl) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        PsiFile file = element1.getContainingFile();
        Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getLastCommittedDocument(file);
        boolean drawSeparator = false;
        if (document != null) {
          CharSequence documentChars = document.getCharsSequence();

          int category = getGroovyCategory(element1, documentChars);
          for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
            int category1 = getGroovyCategory(child, documentChars);
            if (category1 == 0) {
              continue;
            }
            drawSeparator = category != 1 || category1 != 1;
            break;
          }
        }

        if (drawSeparator) {
          GrDocComment comment = null;
          if (element1 instanceof GrDocCommentOwner) {
            comment = ((GrDocCommentOwner)element1).getDocComment();
          }
          LineMarkerInfo info = new LineMarkerInfo<>(element,
                                                     comment != null ? comment.getTextRange() : element.getTextRange(),
                                                     null,
                                                     Pass.LINE_MARKERS,
                                                     FunctionUtil.<Object,
                                                       String>nullConstant(),
                                                     null,
                                                     GutterIconRenderer.Alignment.RIGHT);
          EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return null;
  }

  private static boolean hasSuperMethods(@Nonnull GrMethod method) {
    final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
    if (reflectedMethods.length > 0) {
      for (GrReflectedMethod reflectedMethod : reflectedMethods) {
        final MethodSignatureBackedByPsiMethod first = SuperMethodsSearch.search(reflectedMethod, null, true, false).findFirst();
        if (first != null) {
          return true;
        }
      }
      return false;
    }
    else {
      return SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    }
  }

  private static int getGroovyCategory(@Nonnull PsiElement element, @Nonnull CharSequence documentChars) {
    if (element instanceof GrVariableDeclarationImpl) {
      GrVariable[] variables = ((GrVariableDeclarationImpl)element).getVariables();
      if (variables.length == 1 && variables[0] instanceof GrField && variables[0].getInitializerGroovy() instanceof GrClosableBlock) {
        return 2;
      }
    }

    if (element instanceof GrField || element instanceof GrTypeParameter) {
      return 1;
    }
    if (element instanceof GrTypeDefinition || element instanceof GrClassInitializer) {
      return 2;
    }
    if (element instanceof GrMethod) {
      if (((GrMethod)element).hasModifierProperty(PsiModifier.ABSTRACT) && !(((GrMethod)element).getBlock() != null && GrTraitUtil.isTrait(((GrMethod)element)
                                                                                                                                             .getContainingClass()))) {
        return 1;
      }
      TextRange textRange = element.getTextRange();
      int start = textRange.getStartOffset();
      int end = Math.min(documentChars.length(), textRange.getEndOffset());
      int crlf = StringUtil.getLineBreakCount(documentChars.subSequence(start, end));
      return crlf == 0 ? 1 : 2;
    }
    return 0;
  }

  @Override
  public void collectSlowLineMarkers(@Nonnull final List<PsiElement> elements, @Nonnull final Collection<LineMarkerInfo> result) {
    Set<PsiMethod> methods = new HashSet<>();
    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (element instanceof GrField) {
        methods.addAll(GroovyPropertyUtils.getFieldAccessors((GrField)element));
      }
      else if (element instanceof GrMethod) {
        GrReflectedMethod[] reflected = ((GrMethod)element).getReflectedMethods();
        if (reflected.length != 0) {
          Collections.addAll(methods, reflected);
        }
        else {
          methods.add((PsiMethod)element);
        }
      }
      else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        result.addAll(collectInheritingClasses((PsiClass)element));
      }
    }
    collectOverridingMethods(methods, result);
  }

  private static void collectOverridingMethods(@Nonnull final Set<PsiMethod> methods, @Nonnull Collection<LineMarkerInfo> result) {
    final Set<PsiElement> overridden = new HashSet<>();

    Set<PsiClass> classes = new HashSet<>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      final PsiClass parentClass = method.getContainingClass();
      if (parentClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
    }

    for (final PsiClass aClass : classes) {
      AllOverridingMethodsSearch.search(aClass).forEach(pair -> {
        ProgressManager.checkCanceled();

        final PsiMethod superMethod = pair.getFirst();
        if (isCorrectTarget(superMethod) && isCorrectTarget(pair.getSecond())) {
          if (methods.remove(superMethod)) {
            overridden.add(PsiImplUtil.handleMirror(superMethod));
          }
        }
        return !methods.isEmpty();
      });
    }

    for (PsiElement element : overridden) {
      final Image icon = AllIcons.Gutter.OverridenMethod;

      element = PsiImplUtil.handleMirror(element);

      PsiElement range = element instanceof GrNamedElement ? ((GrNamedElement)element).getNameIdentifierGroovy() : element;

      final MarkerType type =
        element instanceof GrField ? GroovyMarkerTypes.OVERRIDEN_PROPERTY_TYPE : GroovyMarkerTypes.GR_OVERRIDEN_METHOD;
      LineMarkerInfo info = new LineMarkerInfo<>(range,
                                                 range.getTextRange(),
                                                 icon,
                                                 Pass.LINE_MARKERS,
                                                 type.getTooltip(),
                                                 type.getNavigationHandler(),
                                                 GutterIconRenderer.Alignment.RIGHT);
      result.add(info);
    }
  }

  private static boolean isCorrectTarget(@Nonnull PsiMethod method) {
    if (method instanceof GrTraitMethod) {
      return false;
    }

    final PsiElement navigationElement = method.getNavigationElement();
    return method.isPhysical() || navigationElement.isPhysical() && !(navigationElement instanceof PsiClass);
  }
}
