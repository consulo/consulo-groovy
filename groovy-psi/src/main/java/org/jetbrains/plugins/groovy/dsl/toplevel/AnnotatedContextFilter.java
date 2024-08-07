package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

import jakarta.annotation.Nullable;

/**
 * @author peter
 */
public class AnnotatedContextFilter implements ContextFilter {

  private final String myAnnoQName;

  public AnnotatedContextFilter(String annoQName) {
    myAnnoQName = annoQName;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return findContextAnnotation(descriptor.getPlace(), myAnnoQName) != null;
  }

  @Nullable public static PsiAnnotation findContextAnnotation(@Nonnull PsiElement context, String annoQName) {
    PsiElement current = context;
    while (current != null) {
      if (current instanceof PsiModifierListOwner) {
        if (!(current instanceof GrVariableDeclaration)) {
          PsiAnnotation annotation = findAnnotation(((PsiModifierListOwner)current).getModifierList(), annoQName);
          if (annotation != null) {
            return annotation;
          }
        }
      }
      else if (current instanceof PsiFile) {
        if (current instanceof GroovyFile) {
          final GrPackageDefinition packageDefinition = ((GroovyFile)current).getPackageDefinition();
          if (packageDefinition != null) {
            return findAnnotation(packageDefinition.getAnnotationList(), annoQName);
          }
        }
        return null;
      }

      current = current.getContext();
    }

    return null;
  }

  @Nullable
  private static PsiAnnotation findAnnotation(PsiModifierList modifierList, String annoQName) {
    return modifierList != null ? modifierList.findAnnotation(annoQName) : null;
  }



}