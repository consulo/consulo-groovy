// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.impl.annotator.checkers;

import com.intellij.java.language.psi.PsiAnnotation;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrAliasAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    final ArrayList<GrAnnotation> aliasedAnnotations = getAliasedAnnotations(annotation);
    if (aliasedAnnotations == null) {
      return false;
    }

    GrCodeReferenceElement ref = annotation.getClassReference();
    for (GrAnnotation aliased : aliasedAnnotations) {
      PsiElement toHighlight = AliasedAnnotationHolder.findCodeElement(ref, annotation, ref);
      AnnotationChecker.checkApplicability(aliased, annotation.getOwner(), holder, toHighlight);
    }

    return true;
  }

  @Nullable
  private static ArrayList<GrAnnotation> getAliasedAnnotations(GrAnnotation annotation) {
    final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector(annotation);
    if (annotationCollector == null) {
      return null;
    }

    final ArrayList<GrAnnotation> aliasedAnnotations = new ArrayList<>();
    GrAnnotationCollector.collectAnnotations(aliasedAnnotations, annotation, annotationCollector);
    return aliasedAnnotations;
  }

  @Override
  public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
    final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector(annotation);
    if (annotationCollector == null) {
      return false;
    }

    final ArrayList<GrAnnotation> annotations = new ArrayList<>();
    final Set<String> usedAttributes = GrAnnotationCollector.collectAnnotations(annotations, annotation, annotationCollector);

    for (GrAnnotation aliased : annotations) {
      Pair<PsiElement, String> r = AnnotationChecker.checkAnnotationArgumentList(aliased, holder);
      if (r != null && r.getSecond() != null) {
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, r.getSecond());
        PsiElement element = r.getFirst();
        PsiElement highlightElement = element != null && PsiTreeUtil.isAncestor(annotation, element, true) ? element : null;
        if (highlightElement != null) {
          builder = builder.range(highlightElement);
        }
        builder.create();
        return true;
      }
    }

    final GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    final String aliasQName = annotation.getQualifiedName();

    if (attributes.length == 1 && attributes[0].getNameIdentifierGroovy() == null && !usedAttributes.contains("value")) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("at.interface.0.does.not.contain.attribute", aliasQName, "value"))
            .range(attributes[0])
            .create();
    }

    for (GrAnnotationNameValuePair pair : attributes) {
      final PsiElement nameIdentifier = pair.getNameIdentifierGroovy();
      if (nameIdentifier != null && !usedAttributes.contains(pair.getName())) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("at.interface.0.does.not.contain.attribute", aliasQName, pair.getName()))
              .range(nameIdentifier)
              .create();
      }
    }
    return true;
  }
}
