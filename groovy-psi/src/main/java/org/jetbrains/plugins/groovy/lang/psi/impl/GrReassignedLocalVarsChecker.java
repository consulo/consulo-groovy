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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.PsiType;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ref.Ref;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Max Medvedev
 */
public class GrReassignedLocalVarsChecker {

  @Nullable
  public static Boolean isReassignedVar(@Nonnull GrReferenceExpression refExpr) {
    if (!PsiUtil.isCompileStatic(refExpr)) {
      return false;
    }

    if (refExpr.getQualifier() != null) {
      return false;
    }

    final PsiElement resolved = refExpr.resolve();
    if (!PsiUtil.isLocalVariable(resolved)) {
      return false;
    }

    assert resolved instanceof GrVariable;
    return LanguageCachedValueUtil.getCachedValue(resolved, new CachedValueProvider<Boolean>() {
      @Nullable
      @Override
      public Result<Boolean> compute() {
        return Result.create(isReassignedVarImpl((GrVariable)resolved),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  private static boolean isReassignedVarImpl(@Nonnull GrVariable resolved) {
    GrControlFlowOwner variableScope = PsiTreeUtil.getParentOfType(resolved, GrCodeBlock.class,
                                                                         GroovyFile.class);
    if (variableScope == null) {
      return false;
    }

    final String name = resolved.getName();
    final Ref<Boolean> isReassigned = Ref.create(false);
    for (PsiElement scope = resolved.getParent().getNextSibling(); scope != null; scope = scope.getNextSibling()) {
      if (scope instanceof GroovyPsiElement) {
        ((GroovyPsiElement)scope).accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitClosure(GrClosableBlock closure) {
            if (getUsedVarsInsideBlock(closure).contains(name)) {
              isReassigned.set(true);
            }
          }

          @Override
          public void visitElement(GroovyPsiElement element) {
            if (isReassigned.get()) {
              return;
            }
            super.visitElement(element);
          }
        });

        if (isReassigned.get()) {
          break;
        }
      }
    }

    return isReassigned.get();
  }


  @Nullable
  public static PsiType getReassignedVarType(GrReferenceExpression refExpr, boolean honorCompileStatic) {
    if (honorCompileStatic && !PsiUtil.isCompileStatic(refExpr) || refExpr.getQualifier() != null) {
      return null;
    }

    PsiElement resolved = refExpr.resolve();
    if (!PsiUtil.isLocalVariable(resolved)) {
      return null;
    }

    assert resolved instanceof GrVariable;

    return TypeInferenceHelper.getCurrentContext().getExpressionType(((GrVariable)resolved),
																																		 variable -> getLeastUpperBoundByVar(variable));
  }

  @Nullable
  private static PsiType getLeastUpperBoundByVar(@Nonnull final GrVariable var) {
    return RecursionManager.doPreventingRecursion(var, false, new Supplier<PsiType>() {
      @Override
      public PsiType get() {
        Collection<PsiReference> all = ReferencesSearch.search(var, var.getUseScope()).findAll();
        GrExpression initializer = var.getInitializerGroovy();

        if (initializer == null && all.isEmpty()) {
          return var.getDeclaredType();
        }

        PsiType result = initializer != null ? initializer.getType() : null;

        PsiManager manager = var.getManager();
        for (PsiReference reference : all) {
          PsiElement ref = reference.getElement();
          if (ref instanceof GrReferenceExpression && PsiUtil.isLValue(((GrReferenceExpression)ref))) {
            result = TypesUtil.getLeastUpperBoundNullable(result,
                                                          TypeInferenceHelper.getInitializerTypeFor(ref), manager);
          }
        }

        return result;
      }
    });
  }

  @Nonnull
  private static Set<String> getUsedVarsInsideBlock(@Nonnull final GrCodeBlock block) {
    return LanguageCachedValueUtil.getCachedValue(block, new CachedValueProvider<Set<String>>() {
      @Nullable
      @Override
      public Result<Set<String>> compute() {
				final Set<String> result = new HashSet<>();

        block.acceptChildren(new GroovyRecursiveElementVisitor() {

          @Override
          public void visitOpenBlock(GrOpenBlock openBlock) {
            result.addAll(getUsedVarsInsideBlock(openBlock));
          }

          @Override
          public void visitClosure(GrClosableBlock closure) {
            result.addAll(getUsedVarsInsideBlock(closure));
          }

          @Override
          public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
            if (referenceExpression.getQualifier() == null && referenceExpression.getReferenceName() !=
              null) {
              result.add(referenceExpression.getReferenceName());
            }
          }
        });
        return Result.create(result, block);
      }
    });
  }

}
