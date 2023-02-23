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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.ASTNode;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import javax.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class GrSpreadArgumentImpl extends GroovyPsiElementImpl implements GrSpreadArgument {
  public GrSpreadArgumentImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Nonnull
  @Override
  public GrExpression getArgument() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public PsiType getType() {
    return getArgument().getType();
  }

  @Override
  public PsiType getNominalType() {
    return getType();
  }

  @Override
  public GrExpression replaceWithExpression(GrExpression expression, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, expression, removeUnnecessaryParentheses);
  }

  @Override
  public String toString() {
    return "Spread argument";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitSpreadArgument(this);
  }
}
