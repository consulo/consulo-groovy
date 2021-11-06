
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

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