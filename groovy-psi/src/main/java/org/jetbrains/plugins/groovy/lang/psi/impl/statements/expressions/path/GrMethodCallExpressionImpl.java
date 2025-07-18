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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrMethodCallImpl;

import java.util.List;

/**
 * @author ilyas
 */
public class GrMethodCallExpressionImpl extends GrMethodCallImpl implements GrMethodCallExpression, GrCallExpression {

  public GrMethodCallExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "Method call";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethodCallExpression(this);
  }

  @Override
  public GrExpression replaceClosureArgument(@Nonnull GrClosableBlock closure, @Nonnull GrExpression newExpr)
    throws IncorrectOperationException {
    if (newExpr instanceof GrClosableBlock) {
      return closure.replaceWithExpression(newExpr, true);
    }

    final GrClosableBlock[] closureArguments = getClosureArguments();
    final int i = ArrayUtil.find(closureArguments, closure);
    GrArgumentList argList = getArgumentList();
    assert argList!=null;

    if (argList.getText().isEmpty()) {
      argList = (GrArgumentList)argList.replace(GroovyPsiElementFactory.getInstance(getProject()).createArgumentList());
    }

    for (int j = 0; j < i; j++) {
      argList.add(closureArguments[j]);
      closureArguments[j].delete();
    }
    final GrExpression result = (GrExpression)argList.add(newExpr);
    closure.delete();
    return result;
  }

  @Override
  @Nonnull
  public GrClosableBlock[] getClosureArguments() {
    final List<PsiElement> children = findChildrenByType(GroovyElementTypes.CLOSABLE_BLOCK);
    return children.toArray(new GrClosableBlock[children.size()]);
  }
}
