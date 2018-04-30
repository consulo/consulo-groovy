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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import com.intellij.lang.ASTNode;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;

/**
 * @author ven
 */
public class GrInstanceofExpressionImpl extends GrExpressionImpl implements GrInstanceOfExpression {

  public GrInstanceofExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitInstanceofExpression(this);
  }

  public String toString() {
    return "Instanceof expression";
  }

  @Override
  public PsiType getType() {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_BOOLEAN);
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  @Nonnull
  public GrExpression getOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }
}
