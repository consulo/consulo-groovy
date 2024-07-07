package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GroovyExpectedTypesContributor {
  public static final ExtensionPointName<GroovyExpectedTypesContributor> EP_NAME = ExtensionPointName.create
    (GroovyExpectedTypesContributor.class);

  public abstract List<TypeConstraint> calculateTypeConstraints(@Nonnull GrExpression expression);
}
