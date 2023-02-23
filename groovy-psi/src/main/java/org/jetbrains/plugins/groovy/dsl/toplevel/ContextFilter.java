package org.jetbrains.plugins.groovy.dsl.toplevel;

import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

/**
 * @author peter
 */
public interface ContextFilter {
  boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx);


}
