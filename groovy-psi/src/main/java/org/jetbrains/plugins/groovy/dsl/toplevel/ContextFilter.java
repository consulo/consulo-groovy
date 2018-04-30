package org.jetbrains.plugins.groovy.dsl.toplevel;

import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public interface ContextFilter {
  boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx);


}
