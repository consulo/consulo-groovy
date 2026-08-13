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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

/**
 * @author ven
 */
public class GrCaseSectionImpl extends GroovyPsiElementImpl implements GrCaseSection {
  public GrCaseSectionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCaseSection(this);
  }

  @Override
  public String toString() {
    return "Case section";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, state, lastParent, place);
  }

  @Override
  @RequiredWriteAction
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  @RequiredWriteAction
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException
  {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GrCaseLabel[] getCaseLabels() {
    List<GrCaseLabel> labels = findChildrenByType(GroovyElementTypes.CASE_LABEL);
    return labels.toArray(new GrCaseLabel[labels.size()]);
  }

  @Override
  @RequiredReadAction
  public boolean isDefault() {
    List<GrCaseLabel> labels = findChildrenByType(GroovyElementTypes.CASE_LABEL);
    for (GrCaseLabel label : labels) {
      if (label.isDefault()) return true;
    }
    return false;
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public GrStatement[] getStatements() {
    return PsiImplUtil.getStatements(this);
  }

  @Override
  @Nonnull
  @RequiredWriteAction
  public GrStatement addStatementBefore(@Nonnull GrStatement element, @Nullable GrStatement anchor) throws IncorrectOperationException {
    ASTNode elemNode = element.copy().getNode();
    assert elemNode != null;
    ASTNode anchorNode = anchor != null ? anchor.getNode() : null;
    getNode().addChild(elemNode, anchorNode);
    if (mayUseNewLinesAsSeparators()) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mSEMI, ";", anchorNode);
    }
    return (GrStatement)elemNode.getPsi();
  }

  private boolean mayUseNewLinesAsSeparators() {
    PsiElement parent = this;
    while (parent != null) {
      if (parent instanceof GrString grString) {
        return !grString.isPlainString();
      }
      parent = parent.getParent();
    }
    return true;
  }
}
