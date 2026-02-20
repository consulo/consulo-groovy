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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.function.Function;

/**
 * @author ven
 */
public class GrSafeCastExpressionImpl extends GrExpressionImpl implements GrSafeCastExpression, PsiPolyVariantReference {

  private static final Function<GrSafeCastExpressionImpl, PsiType> TYPE_CALCULATOR =
    new Function<GrSafeCastExpressionImpl, PsiType>() {
      @Override
      public PsiType apply(GrSafeCastExpressionImpl cast) {
        GrTypeElement typeElement = cast.getCastTypeElement();
        if (typeElement == null) return null;

        PsiType opType = cast.getOperand().getType();
        PsiType castType = typeElement.getType();

        if (isCastToRawCollectionFromArray(opType, castType)) {
          PsiClass resolved = ((PsiClassType)castType).resolve();
          assert resolved != null;
          PsiTypeParameter typeParameter = resolved.getTypeParameters()[0];
          HashMap<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
          substitutionMap.put(typeParameter, TypesUtil.getItemType(opType));
          PsiSubstitutor substitutor = JavaPsiFacade.getElementFactory(cast.getProject()).createSubstitutor(substitutionMap);
          return JavaPsiFacade.getElementFactory(cast.getProject()).createType(resolved, substitutor);
        }

        GrTraitType traitClassType = GrTraitType.createTraitClassType(cast);
        if (traitClassType != null) {
          return traitClassType;
        }

        return castType;//TypesUtil.boxPrimitiveType(castType, cast.getManager(), cast.getResolveScope());
      }
    };


  /**
   * It is assumed that collection class should have only one type param and this param defines collection's item type.
   */
  @SuppressWarnings("ConstantConditions")
  private static boolean isCastToRawCollectionFromArray(PsiType opType, PsiType castType) {
    return castType instanceof PsiClassType &&
      InheritanceUtil.isInheritor(castType, CommonClassNames.JAVA_UTIL_COLLECTION) &&
      PsiUtil.extractIterableTypeParameter(castType, false) == null &&
      ((PsiClassType)castType).resolve().getTypeParameters().length == 1 &&
      TypesUtil.getItemType(opType) != null;
  }


  private static final class OurResolver implements ResolveCache.PolyVariantResolver<GrSafeCastExpressionImpl> {
    @Nonnull
    @Override
    public ResolveResult[] resolve(@Nonnull GrSafeCastExpressionImpl cast, boolean incompleteCode) {
      GrExpression operand = cast.getOperand();
      PsiType type = operand.getType();
      if (type == null) {
        return GroovyResolveResult.EMPTY_ARRAY;
      }

      GrTypeElement typeElement = cast.getCastTypeElement();
      PsiType toCast = typeElement == null ? null : typeElement.getType();
      PsiType classType = TypesUtil.createJavaLangClassType(toCast, cast.getProject(), cast.getResolveScope());
      return TypesUtil.getOverloadedOperatorCandidates(type, GroovyTokenTypes.kAS, operand, new PsiType[]{classType});
    }
  }

  private static final OurResolver OUR_RESOLVER = new OurResolver();

  public GrSafeCastExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitSafeCastExpression(this);
  }

  public String toString() {
    return "Safe cast expression";
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Override
  @Nullable
  public GrTypeElement getCastTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  @Nonnull
  public GrExpression getOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    PsiElement as = findNotNullChildByType(GroovyTokenTypes.kAS);
    int offset = as.getStartOffsetInParent();
    return new TextRange(offset, offset + 2);
  }

  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false)).getElement();
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new UnsupportedOperationException("safe cast cannot be renamed");
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException("safe cast can be bounded to nothing");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, OUR_RESOLVER);
  }
}
