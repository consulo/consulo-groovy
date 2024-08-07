/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types;

import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
public class GrTypeCastExpressionImpl extends GrExpressionImpl implements GrTypeCastExpression {

  public GrTypeCastExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCastExpression(this);
  }

  public String toString() {
    return "Typecast expression";
  }

  @Override
  public PsiType getType() {
    final GrTypeElement typeElement = getCastTypeElement();
    return typeElement != null ? TypesUtil.boxPrimitiveType(typeElement.getType(), getManager(), getResolveScope()) : null;
  }

  @Override
  public GrTypeElement getCastTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  @Nullable
  public GrExpression getOperand() {
    return findExpressionChild(this);
  }

  @Override
  @Nonnull
  public PsiElement getLeftParen() {
    ASTNode paren = getNode().findChildByType(GroovyTokenTypes.mLPAREN);
    assert paren != null;
    return paren.getPsi();
  }

  @Override
  @Nonnull
  public PsiElement getRightParen() {
    ASTNode paren = getNode().findChildByType(GroovyTokenTypes.mRPAREN);
    assert paren != null;
    return paren.getPsi();
  }
}
