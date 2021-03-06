package org.jetbrains.plugins.groovy.dsl.toplevel;

import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

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