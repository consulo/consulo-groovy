package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.java.language.psi.PsiType;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import jakarta.annotation.Nullable;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public interface GrVariableDeclaration extends GrStatement, GrMembersDeclaration {
  ArrayFactory<GrVariableDeclaration> ARRAY_FACTORY = GrVariableDeclaration[]::new;

  @Nullable
  GrTypeElement getTypeElementGroovy();

  @Nonnull
  GrVariable[] getVariables();

  void setType(@Nullable PsiType type);

  boolean isTuple();

  @Nullable
  GrTypeElement getTypeElementGroovyForVariable(GrVariable var);

  @Nullable
  GrExpression getTupleInitializer();

  @Override
  boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @Nonnull String name);
}
