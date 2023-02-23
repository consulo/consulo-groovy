
package org.jetbrains.plugins.groovy.lang.psi.api.util;

import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author ilyas
 */
public interface GrVariableDeclarationOwner extends GroovyPsiElement {

  /**
   * Removes variable from its declaration. In case of alone variablein declaration,
   * it also will be removed.
   * @param variable to remove
   * @throws IncorrectOperationException in case the operation cannot be performed
   */
  void removeVariable(GrVariable variable);

  /**
   * Adds new variable declaration after anchor spectified. If anchor == null, adds variable at owner's first position
   * @param declaration declaration to insert 
   * @param anchor Anchor after which new variabler declaration will be placed
   * @return inserted variable declaration
   * @throws IncorrectOperationException in case the operation cannot be performed
   */
  GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException;

}
