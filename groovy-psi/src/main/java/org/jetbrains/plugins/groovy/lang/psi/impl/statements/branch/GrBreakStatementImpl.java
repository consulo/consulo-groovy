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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import consulo.language.ast.ASTNode;

/**
 * @author ilyas
 */
public class GrBreakStatementImpl extends GrFlowInterruptingStatementImpl implements GrBreakStatement {
  public GrBreakStatementImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitBreakStatement(this);
  }

  public String toString() {
    return "BREAK statement";
  }

  @Override
  @Nullable
  public GrStatement findTargetStatement() {
    return ResolveUtil.resolveLabelTargetStatement(getLabelName(), this, true);
  }

  @Override
  public GrLabeledStatement resolveLabel() {
    return ResolveUtil.resolveLabeledStatement(getLabelName(), this, true);
  }

  @Override
  public String getStatementText() {
    return "break";
  }
}
