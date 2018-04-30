
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import javax.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

/**
 * @author ilyas
 */
public interface GrFinallyClause extends GroovyPsiElement {

  @Nonnull
  GrOpenBlock getBody();

}