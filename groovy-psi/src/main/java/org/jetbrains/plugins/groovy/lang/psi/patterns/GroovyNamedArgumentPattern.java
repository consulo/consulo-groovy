package org.jetbrains.plugins.groovy.lang.psi.patterns;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.pattern.ElementPattern;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.StringPattern;

/**
 * @author Sergey Evdokimov
 */
public class GroovyNamedArgumentPattern extends GroovyElementPattern<GrNamedArgument, GroovyNamedArgumentPattern> {

  public GroovyNamedArgumentPattern() {
    super(GrNamedArgument.class);
  }

  public GroovyNamedArgumentPattern withLabel(@Nonnull final String label) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@Nonnull GrNamedArgument namedArgument, final ProcessingContext context) {
        return label.equals(namedArgument.getLabelName());
      }
    });
  }

  public GroovyNamedArgumentPattern withLabel(@Nonnull final StringPattern labelPattern) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@Nonnull GrNamedArgument namedArgument, final ProcessingContext context) {
        return labelPattern.getCondition().accepts(namedArgument.getLabelName(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern withExpression(@Nonnull final ElementPattern pattern) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@Nonnull GrNamedArgument namedArgument, final ProcessingContext context) {
        return pattern.getCondition().accepts(namedArgument.getExpression(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern isParameterOfMethodCall(@Nullable final ElementPattern<? extends GrCall> methodCall) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@Nonnull GrNamedArgument namedArgument, final ProcessingContext context) {
        GrCall call = PsiUtil.getCallByNamedParameter(namedArgument);

        return call != null && (methodCall == null || methodCall.accepts(call, context));
      }
    });
  }

}
