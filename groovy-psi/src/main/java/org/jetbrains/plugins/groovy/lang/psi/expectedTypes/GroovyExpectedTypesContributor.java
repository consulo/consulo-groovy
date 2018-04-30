package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author peter
 */
public abstract class GroovyExpectedTypesContributor {
  public static final ExtensionPointName<GroovyExpectedTypesContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.expectedTypesContributor");

  public abstract List<TypeConstraint> calculateTypeConstraints(@Nonnull GrExpression expression);
}
