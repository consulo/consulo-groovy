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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.psi.ASTDelegatePsiElement;
import consulo.language.impl.psi.CheckUtil;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.util.dataholder.Key;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
public abstract class GrBlockImpl extends LazyParseablePsiElement implements GrCodeBlock, GrControlFlowOwner {
  private static final Key<CachedValue<Instruction[]>> CONTROL_FLOW = Key.create("Control flow");

  protected GrBlockImpl(@Nonnull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    if (getParent() instanceof ASTDelegatePsiElement) {
      CheckUtil.checkWritable(this);
      ((ASTDelegatePsiElement)getParent()).deleteChildInternal(getNode());
    }
    else {
      getParent().deleteChildRange(this, this);
    }
  }

  @Override
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyPsiElementImpl.removeElements(this, elements);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    GroovyPsiElementImpl.acceptGroovyChildren(this, visitor);
  }

  public <T extends GrStatement> T replaceWithStatement(T statement) {
    return GroovyPsiElementImpl.replaceWithStatement(this, statement);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    putUserData(CONTROL_FLOW, null);
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    final PsiElement element = child.getPsi();
    if (element instanceof GrStatement) {
      PsiImplUtil.deleteStatementTail(this, element);
    }
    super.deleteChildInternal(child);
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

  @Override
  public Instruction[] getControlFlow() {
    assert isValid();
    CachedValue<Instruction[]> controlFlow = getUserData(CONTROL_FLOW);
    if (controlFlow == null) {
      controlFlow = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Instruction[]>() {
        @Override
        public Result<Instruction[]> compute() {
          try {
            //ResolveProfiler.start();
            final Instruction[] flow = new ControlFlowBuilder(getProject()).buildControlFlow(GrBlockImpl.this);
            return Result.create(flow, getContainingFile(), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
          finally {
            //final long time = ResolveProfiler.finish();
            //ResolveProfiler.write("flow", GrBlockImpl.this, time);
          }
        }
      }, false);
      controlFlow = putUserDataIfAbsent(CONTROL_FLOW, controlFlow);
    }
    return ControlFlowBuilder.assertValidPsi(controlFlow.getValue());
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  private boolean mayUseNewLinesAsSeparators() {
    PsiElement parent = this;
    while (parent != null) {
      if (parent instanceof GrString) {
        GrString grString = (GrString) parent;
        return !grString.isPlainString();
      }
      parent = parent.getParent();
    }
    return true;
  }

  @Override
  @Nonnull
  public GrStatement[] getStatements() {
    return  PsiImplUtil.getStatements(this);
  }

  @Override
  @Nonnull
  public GrStatement addStatementBefore(@Nonnull GrStatement element, @Nullable GrStatement anchor) throws IncorrectOperationException {
    if (anchor == null && getRBrace() == null) {
      throw new IncorrectOperationException();
    }

    if (anchor != null && !this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }

    final LeafElement nls = Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n", 0, 1, null, getManager());

    PsiElement actualAnchor = anchor == null ? getRBrace() : anchor;
    if (mayUseNewLinesAsSeparators()) {
      PsiElement prev = actualAnchor.getPrevSibling();
      if (prev instanceof GrParameterList && prev.getTextLength() == 0 && prev.getPrevSibling() != null) {
        prev = prev.getPrevSibling();
      }
      if (!PsiUtil.isLineFeed(prev)) {
        addBefore(nls.getPsi(), actualAnchor);
      }
    }
    element = (GrStatement)addBefore(element, actualAnchor);
    if (mayUseNewLinesAsSeparators()) {
      addBefore(nls.getPsi(), actualAnchor);
    }
    else {
      addBefore(Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n", 0, 1, null, getManager()).getPsi(), actualAnchor);
    }
    return element;
  }

  @Override
  public PsiElement getLBrace() {
    return findPsiChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Override
  @Nullable
  public PsiElement getRBrace() {
    return findPsiChildByType(GroovyTokenTypes.mRCURLY);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, state, lastParent, place);
  }
}
