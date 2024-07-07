
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
public interface GrCatchClause extends GrParameterListOwner
{

  @Nullable
  GrParameter getParameter();

  @Nullable
  GrOpenBlock getBody();

  @Nullable
  PsiElement getLBrace();

  @Nullable
  PsiElement getRParenth();
}