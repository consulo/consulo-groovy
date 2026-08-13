package org.jetbrains.plugins.groovy.dsl.toplevel;

import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

import java.util.List;

/**
 * @author peter
 */
public class CompositeContextFilter implements ContextFilter {
  private final List<ContextFilter> myFilters;
  private final boolean myAnd;

  private CompositeContextFilter(List<ContextFilter> filters, boolean and) {
    myFilters = filters;
    myAnd = and;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    for (ContextFilter filter : myFilters) {
      if (myAnd != filter.isApplicable(descriptor, ctx)) {
        return !myAnd;
      }
    }
    return myAnd;
  }

  @Nonnull
  public static ContextFilter compose(@Nonnull List<ContextFilter> filters, boolean and) {
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return new CompositeContextFilter(filters, and);
  }
}
