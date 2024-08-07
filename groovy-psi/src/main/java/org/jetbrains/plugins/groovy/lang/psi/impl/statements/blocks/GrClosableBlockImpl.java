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

import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import groovy.lang.Closure;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Function;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {
  private volatile GrParameter[] mySyntheticItParameter;

  public GrClosableBlockImpl(@Nonnull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    mySyntheticItParameter = null;
  }

  @Override
  public boolean processClosureDeclarations(@Nonnull final PsiScopeProcessor plainProcessor,
                                            @Nonnull final PsiScopeProcessor nonCodeProcessor,
                                            @Nonnull final ResolveState state,
                                            @Nullable final PsiElement lastParent,
                                            @Nonnull final PsiElement place) {
    if (!processDeclarations(plainProcessor, state, lastParent, place)) {
      return false;
    }
    if (!processOwnerAndDelegate(plainProcessor, nonCodeProcessor, state, place)) {
      return false;
    }

    return true;
  }

  @Override
  public boolean processDeclarations(@Nonnull final PsiScopeProcessor processor,
                                     @Nonnull final ResolveState state,
                                     @Nullable final PsiElement lastParent,
                                     @Nonnull final PsiElement place) {
    if (lastParent == null) {
      return true;
    }

    if (!super.processDeclarations(processor, state, lastParent, place)) {
      return false;
    }
    if (!processParameters(processor, state, place)) {
      return false;
    }
    if (ResolveUtil.shouldProcessProperties(processor.getHint(ClassHint.KEY))) {
      PsiVariable owner = new GrLightVariable(getManager(), OWNER_NAME, getOwnerType(), GrClosableBlockImpl.this);

      if (!ResolveUtil.processElement(processor, owner, state)) {
        return false;
      }
    }
    if (!processClosureClassMembers(processor, state, lastParent, place)) {
      return false;
    }

    return true;
  }

  private boolean processOwnerAndDelegate(@Nonnull PsiScopeProcessor processor,
                                          @Nonnull PsiScopeProcessor nonCodeProcessor,
                                          @Nonnull ResolveState state,
                                          @Nonnull PsiElement place) {
    Boolean result = processDelegatesTo(processor, nonCodeProcessor, state, place);
    if (result != null) {
      return result.booleanValue();
    }

    if (!processOwner(processor, nonCodeProcessor, state, place)) {
      return false;
    }
    return true;
  }

  @Nullable
  private Boolean processDelegatesTo(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull PsiScopeProcessor nonCodeProcessor,
                                     @Nonnull ResolveState state,
                                     @Nonnull PsiElement place) {
    GrDelegatesToUtil.DelegatesToInfo info = GrDelegatesToUtil.getDelegatesToInfo(place, this);
    if (info == null) {
      return null;
    }

    switch (info.getStrategy()) {
      case Closure.OWNER_FIRST:
        if (!processOwner(processor, nonCodeProcessor, state, place)) {
          return false;
        }
        if (!processDelegate(processor, nonCodeProcessor, state, place, info.getTypeToDelegate())) {
          return false;
        }
        return true;
      case Closure.DELEGATE_FIRST:
        if (!processDelegate(processor, nonCodeProcessor, state, place, info.getTypeToDelegate())) {
          return false;
        }
        if (!processOwner(processor, nonCodeProcessor, state, place)) {
          return false;
        }
        return true;
      case Closure.OWNER_ONLY:
        if (!processOwner(processor, nonCodeProcessor, state, place)) {
          return false;
        }
        return true;
      case Closure.DELEGATE_ONLY:
        if (!processDelegate(processor, nonCodeProcessor, state, place, info.getTypeToDelegate())) {
          return false;
        }
        return true;
      case Closure.TO_SELF:
        return true;
      default:
        return null;
    }
  }

  private boolean processDelegate(@Nonnull PsiScopeProcessor processor,
                                  @Nonnull PsiScopeProcessor nonCodeProcessor,
                                  @Nonnull ResolveState state,
                                  @Nonnull PsiElement place,
                                  @Nullable final PsiType classToDelegate) {
    if (classToDelegate == null) {
      return true;
    }

    return ResolveUtil.processAllDeclarationsSeparately(classToDelegate, processor, nonCodeProcessor,
                                                        state.put(ClassHint.RESOLVE_CONTEXT, this), place);
  }

  private boolean processClosureClassMembers(@Nonnull PsiScopeProcessor processor,
                                             @Nonnull ResolveState state,
                                             @Nullable PsiElement lastParent,
                                             @Nonnull PsiElement place) {
    final PsiClass closureClass =
      GroovyPsiManager.getInstance(getProject()).findClassWithCache(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, getResolveScope());
    if (closureClass == null) {
      return true;
    }

    return closureClass.processDeclarations(processor, state.put(ClassHint.RESOLVE_CONTEXT, this), lastParent, place);
  }

  private boolean processParameters(@Nonnull PsiScopeProcessor processor,
                                    @Nonnull ResolveState state,
                                    @Nonnull PsiElement place) {
    if (!ResolveUtil.shouldProcessProperties(processor.getHint(ClassHint.KEY))) {
      return true;
    }

    if (hasParametersSection()) {
      for (GrParameter parameter : getParameters()) {
        if (!ResolveUtil.processElement(processor, parameter, state)) {
          return false;
        }
      }
    }
    else if (!isItAlreadyDeclared(place)) {
      GrParameter[] synth = getSyntheticItParameter();
      if (synth.length > 0) {
        if (!ResolveUtil.processElement(processor, synth[0], state.put(ClassHint.RESOLVE_CONTEXT, this))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean processOwner(@Nonnull PsiScopeProcessor processor,
                               @Nonnull PsiScopeProcessor nonCodeProcessor,
                               @Nonnull ResolveState state,
                               @Nonnull PsiElement place) {
    final PsiElement parent = getParent();
    if (parent == null) {
      return true;
    }

    if (!ResolveUtil.processStaticImports(processor, getContainingFile(), state, place)) {
      return false;
    }

    return ResolveUtil.doTreeWalkUp(parent, place, processor, nonCodeProcessor, state);
  }

  private boolean isItAlreadyDeclared(@Nullable PsiElement place) {
    while (place != this && place != null) {
      if (place instanceof GrClosableBlock &&
        !((GrClosableBlock)place).hasParametersSection() &&
        !(place.getParent() instanceof GrStringInjection)) {
        return true;
      }
      place = place.getParent();
    }
    return false;
  }

  public String toString() {
    return "Closable block";
  }

  @Override
  public GrParameter[] getParameters() {
    if (hasParametersSection()) {
      GrParameterListImpl parameterList = getParameterList();
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  @Override
  public GrParameter[] getAllParameters() {
    if (getParent() instanceof GrStringInjection) {
      return GrParameter.EMPTY_ARRAY;
    }
    if (hasParametersSection()) {
      return getParameters();
    }
    return getSyntheticItParameter();
  }

  @Override
  @Nullable
  public PsiElement getArrow() {
    return findPsiChildByType(GroovyTokenTypes.mCLOSABLE_BLOCK_OP);
  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(getParameters());
  }


  @Override
  @Nonnull
  public GrParameterListImpl getParameterList() {
    final GrParameterListImpl childByClass = findChildByClass(GrParameterListImpl.class);
    assert childByClass != null;
    return childByClass;
  }

  @Override
  public GrParameter addParameter(GrParameter parameter) {
    GrParameterList parameterList = getParameterList();
    if (getArrow() == null) {
      final GrParameterList newParamList = (GrParameterList)addAfter(parameterList, getLBrace());
      parameterList.delete();
      ASTNode next = newParamList.getNode().getTreeNext();
      getNode().addLeaf(GroovyTokenTypes.mCLOSABLE_BLOCK_OP, "->", next);
      return (GrParameter)newParamList.add(parameter);
    }

    return (GrParameter)parameterList.add(parameter);
  }

  @Override
  public boolean hasParametersSection() {
    return getArrow() != null;
  }

  @Override
  public PsiType getType() {
    return GrClosureType.create(this, true);
  }

  @Override
  @Nullable
  public PsiType getNominalType() {
    return getType();
  }

  public GrParameter[] getSyntheticItParameter() {
    if (getParent() instanceof GrStringInjection) {
      return GrParameter.EMPTY_ARRAY;
    }

    GrParameter[] res = mySyntheticItParameter;
    if (res == null) {
      res = new GrParameter[]{new ClosureSyntheticParameter(this)};
      synchronized (this) {
        if (mySyntheticItParameter == null) {
          mySyntheticItParameter = res;
        }
      }
    }

    return res;
  }

  @Nonnull
  public PsiType getOwnerType() {
    return LanguageCachedValueUtil.getCachedValue(this, () -> {
      final GroovyPsiElement context =
        PsiTreeUtil.getParentOfType(GrClosableBlockImpl.this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      PsiType type = null;
      if (context instanceof GrTypeDefinition) {
        type = factory.createType((PsiClass)context);
      }
      else if (context instanceof GrClosableBlock) {
        type = GrClosureType.create((GrClosableBlock)context, true);
      }
      else if (context instanceof GroovyFile) {
        final PsiClass scriptClass = ((GroovyFile)context).getScriptClass();
        if (scriptClass != null && GroovyNamesUtil.isIdentifier(scriptClass.getName())) {
          type = factory.createType(scriptClass);
        }
      }

      if (type == null) {
        type = TypesUtil.getJavaLangObject(GrClosableBlockImpl.this);
      }

      return CachedValueProvider.Result.create(type, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  public GrExpression replaceWithExpression(@Nonnull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  private static final Function<GrClosableBlock, PsiType> ourTypesCalculator =
    block -> GroovyPsiManager.inferType(block, new MethodTypeInferencer(block));

  @Override
  @Nullable
  public PsiType getReturnType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, ourTypesCalculator);
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return !(getParent() instanceof GrStringInjection);
  }
}
