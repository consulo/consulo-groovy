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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

/**
 * @autor: ilyas
 */
public class GrForStatementImpl extends GroovyPsiElementImpl implements GrForStatement {
  public GrForStatementImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitForStatement(this);
  }

  public String toString() {
    return "For statement";
  }

  @Override
  @Nullable
  public GrForClause getClause() {
    return findChildByClass(GrForClause.class);
  }

  @Override
  @Nullable
  public GrStatement getBody() {
    return findChildByClass(GrStatement.class);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (!ResolveUtil.shouldProcessProperties(processor.getHint(ClassHint.KEY))) return true;

    GrForClause forClause = getClause();
    final GrVariable varScope = PsiTreeUtil.getParentOfType(place, GrVariable.class);
    if (forClause == null) return true;
    if (lastParent == null || lastParent instanceof GrForInClause) return true;

    GrVariable var = forClause.getDeclaredVariable();
    if (var == null || var.equals(varScope)) return true;
    if (!ResolveUtil.processElement(processor, var, state)) return false;

    return true;
  }

  @Override
  public <T extends GrCondition> T replaceBody(T newBody) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBody, getBody(), getNode(), getProject());
  }

  @Override
  public PsiElement getRParenth() {
    return findChildByType(GroovyTokenTypes.mRPAREN);
  }

  @Override
  public PsiElement getLParenth() {
    return findChildByType(GroovyTokenTypes.mLPAREN);
  }
}
