package org.jetbrains.plugins.groovy.dsl.toplevel;

import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

/**
 * @author peter
 */
public class FileContextFilter implements ContextFilter {
  private final ElementPattern<? extends PsiFile> myPattern;

  public FileContextFilter(ElementPattern<? extends PsiFile> pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return myPattern.accepts(descriptor.getPlaceFile(), ctx);
  }
}