package org.jetbrains.plugins.groovy.dsl.toplevel;

import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

/**
 * @author peter
 */
public class PlaceContextFilter implements ContextFilter {
  private final ElementPattern<PsiElement> myPattern;

  public PlaceContextFilter(ElementPattern<PsiElement> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return myPattern.accepts(descriptor.getPlace(), ctx);
  }

}