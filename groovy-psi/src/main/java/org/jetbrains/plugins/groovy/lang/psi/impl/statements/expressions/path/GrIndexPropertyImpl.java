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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayFactory;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBuiltinTypeClassExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrImmediateTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Function;

/**
 * @author ilyas
 */
public class GrIndexPropertyImpl extends GrExpressionImpl implements GrIndexProperty {

  private static final Function<GrIndexPropertyImpl, PsiType> TYPE_CALCULATOR = index -> index.inferType(null);
  private static final ResolveCache.PolyVariantResolver<MyReference> RESOLVER = new ResolveCache.PolyVariantResolver<MyReference>() {
    @Nonnull
    @Override
    public GroovyResolveResult[] resolve(@Nonnull MyReference reference, boolean incompleteCode) {
      final GrIndexPropertyImpl index = reference.getElement();
      return index.resolveImpl(incompleteCode, null, null);
    }
  };

  private final MyReference myReference = new MyReference();

  private PsiType inferType(@Nullable Boolean isSetter) {
    GrExpression selected = getInvokedExpression();
    PsiType thisType = selected.getType();

    if (thisType == null) {
      thisType = TypesUtil.getJavaLangObject(this);
    }


    GrArgumentList argList = getArgumentList();

    PsiType[] argTypes = PsiUtil.getArgumentTypes(argList);
    if (argTypes == null) return null;

    final PsiManager manager = getManager();
    final GlobalSearchScope resolveScope = getResolveScope();

    if (argTypes.length == 0) {
      PsiType arrType = null;
      if (selected instanceof GrBuiltinTypeClassExpression) {
        arrType = ((GrBuiltinTypeClassExpression)selected).getPrimitiveType();
      }

      if (selected instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)selected).resolve();
        if (resolved instanceof PsiClass) {
          String qname = ((PsiClass)resolved).getQualifiedName();
          if (qname != null) {
            arrType = TypesUtil.createTypeByFQClassName(qname, this);
          }
        }
      }

      if (arrType != null) {
        final PsiArrayType param = arrType.createArrayType();
        return TypesUtil.createJavaLangClassType(param, getProject(), resolveScope);
      }
    }

    if (PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, this, isSetter != null ? isSetter.booleanValue() : PsiUtil.isLValue(this))) {
      return TypesUtil.boxPrimitiveType(((PsiArrayType)thisType).getComponentType(), manager, resolveScope);
    }

    final GroovyResolveResult[] candidates;
    if (isSetter != null) {
      candidates = isSetter.booleanValue() ? multiResolveSetter(false) : multiResolveGetter(false);
    }
    else {
      candidates = multiResolveGroovy(false);
    }


    //don't use short PsiUtil.getArgumentTypes(...) because it use incorrect 'isSetter' value
    PsiType[] args = PsiUtil
      .getArgumentTypes(argList.getNamedArguments(), argList.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY, true, null, false);
    final GroovyResolveResult candidate = PsiImplUtil.extractUniqueResult(candidates);
    final PsiElement element = candidate.getElement();
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      if ("putAt".equals(name) && args != null) {
        args = ArrayUtil.append(args, TypeInferenceHelper.getInitializerTypeFor(this), PsiType.class);
      }
    }
    PsiType overloadedOperatorType = ResolveUtil.extractReturnTypeFromCandidate(candidate, this, args);

    PsiType componentType = extractMapValueType(thisType, args, manager, resolveScope);

    if (overloadedOperatorType != null &&
        (componentType == null || !TypesUtil.isAssignableByMethodCallConversion(overloadedOperatorType, componentType, selected))) {
      return TypesUtil.boxPrimitiveType(overloadedOperatorType, manager, resolveScope);
    }
    return componentType;
  }

  @Nullable
  private static PsiType extractMapValueType(PsiType thisType, PsiType[] argTypes, PsiManager manager, GlobalSearchScope resolveScope) {
    if (argTypes.length != 1 || !InheritanceUtil.isInheritor(thisType, CommonClassNames.JAVA_UTIL_MAP)) return null;
    final PsiType substituted = com.intellij.java.language.psi.util.PsiUtil.substituteTypeParameter(thisType, CommonClassNames.JAVA_UTIL_MAP, 1, true);
    return TypesUtil.boxPrimitiveType(substituted, manager, resolveScope);
  }


  private GroovyResolveResult[] resolveImpl(boolean incompleteCode, @Nullable GrExpression upToArgument, @Nullable Boolean isSetter) {
    if (isSetter == null) isSetter = PsiUtil.isLValue(this);

    GrExpression invoked = getInvokedExpression();
    PsiType thisType = invoked.getType();

    if (thisType == null) {
      thisType = TypesUtil.getJavaLangObject(this);
    }

    GrArgumentList argList = getArgumentList();

    //don't use short PsiUtil.getArgumentTypes(...) because it use incorrect 'isSetter' value
    PsiType[] argTypes = PsiUtil.getArgumentTypes(argList.getNamedArguments(), argList.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY, true, upToArgument, false);
    if (argTypes == null) return GroovyResolveResult.EMPTY_ARRAY;

    final GlobalSearchScope resolveScope = getResolveScope();

    if (argTypes.length == 0) {
      PsiType arrType = null;
      if (invoked instanceof GrBuiltinTypeClassExpression) {
        arrType = ((GrBuiltinTypeClassExpression)invoked).getPrimitiveType();
      }

      if (invoked instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)invoked).resolve();
        if (resolved instanceof PsiClass) {
          String qname = ((PsiClass)resolved).getQualifiedName();
          if (qname != null) {
            arrType = TypesUtil.createTypeByFQClassName(qname, this);
          }
        }
      }

      if (arrType != null) {
        return GroovyResolveResult.EMPTY_ARRAY;
      }
    }

    GroovyResolveResult[] candidates;
    final String name = isSetter ? "putAt" : "getAt";
    if (isSetter && !incompleteCode) {
      argTypes = ArrayUtil.append(argTypes, TypeInferenceHelper.getInitializerTypeFor(this), PsiType.class);
    }

    if (PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, this, isSetter)) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    candidates = ResolveUtil.getMethodCandidates(thisType, name, invoked, true, incompleteCode, false, argTypes);

    //hack for remove DefaultGroovyMethods.getAt(Object, ...)
    if (candidates.length == 2) {
      for (int i = 0; i < candidates.length; i++) {
        GroovyResolveResult candidate = candidates[i];
        final PsiElement element = candidate.getElement();
        if (element instanceof GrGdkMethod) {
          final PsiMethod staticMethod = ((GrGdkMethod)element).getStaticMethod();
          final PsiParameter param = staticMethod.getParameterList().getParameters()[0];
          if (param.getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            return new GroovyResolveResult[]{candidates[1 - i]};
          }
        }
      }
    }

    if (candidates.length != 1) {
      final GrTupleType tupleType = new GrImmediateTupleType(argTypes, JavaPsiFacade.getInstance(getProject()), resolveScope);
      final GroovyResolveResult[] tupleCandidates = ResolveUtil.getMethodCandidates(thisType, name, invoked, tupleType);
      if (incompleteCode) {
        candidates = ArrayUtil.mergeArrays(candidates, tupleCandidates, new ArrayFactory<GroovyResolveResult>() {
          @Nonnull
          @Override
          public GroovyResolveResult[] create(int count) {
            return new GroovyResolveResult[count];
          }
        });
      }
      else {
        candidates = tupleCandidates;
      }
    }

    return candidates;
  }

  public GrIndexPropertyImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitIndexProperty(this);
  }

  public String toString() {
    return "Property by index";
  }

  @Override
  @Nonnull
  public GrExpression getInvokedExpression() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  @Nonnull
  public GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }

  @Nullable
  @Override
  public PsiType getGetterType() {
    return inferType(false);
  }

  @Nullable
  @Override
  public PsiType getSetterType() {
    return inferType(true);
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolveGetter(boolean incomplete) {
    return resolveImpl(incomplete, null, false);
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolveSetter(boolean incomplete) {
    return resolveImpl(incomplete, null, true);
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolveGroovy(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(myReference, incompleteCode, RESOLVER);
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Override
  public PsiType getNominalType() {
    if (getParent() instanceof GrThrowStatement) return super.getNominalType();

    GroovyResolveResult[] candidates = multiResolveGroovy(true);
    if (candidates.length == 1) {
      return extractLastParameterType(candidates[0]);
    }
    return null;
  }

  @Nullable
  private PsiType extractLastParameterType(GroovyResolveResult candidate) {
    PsiElement element = candidate.getElement();
    if (element instanceof PsiMethod) {
      PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
      if (parameters.length > 1) {
        PsiParameter last = parameters[parameters.length - 1];
        return TypesUtil.substituteAndNormalizeType(last.getType(), candidate.getSubstitutor(), candidate.getSpreadState(), this);
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    GrArgumentList list = getArgumentList();
    return list.getNamedArguments();
  }

  @Nonnull
  @Override
  public GrExpression[] getExpressionArguments() {
    GrArgumentList list = getArgumentList();
    return list.getExpressionArguments();
  }

  @Override
  public GrNamedArgument addNamedArgument(GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrArgumentList list = getArgumentList();
    return list.addNamedArgument(namedArgument);
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    if (upToArgument == null) {
      return multiResolveGroovy(true);
    }
    return resolveImpl(true, upToArgument, null);
  }

  @Nonnull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod resolveMethod() {
    return PsiImplUtil.extractUniqueElement(multiResolveGroovy(false));
  }

  @Nonnull
  @Override
  public GroovyResolveResult advancedResolve() {
    GroovyResolveResult[] results = multiResolveGroovy(false);
    return results.length == 1 ? results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @Override
  public PsiReference getReference() {
    return myReference;
  }

  private class MyReference implements PsiPolyVariantReference {
    @RequiredReadAction
    @Override
    public GrIndexPropertyImpl getElement() {
      return GrIndexPropertyImpl.this;
    }

    @RequiredReadAction
    @Override
    public TextRange getRangeInElement() {
      final int offset = getArgumentList().getStartOffsetInParent();
      return new TextRange(offset, offset + 1);
    }

    @RequiredReadAction
    @Override
    public PsiElement resolve() {
      return resolveMethod();
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public String getCanonicalText() {
      return "Array-style access";
    }

    @RequiredWriteAction
    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return GrIndexPropertyImpl.this;
    }

    @RequiredWriteAction
    @Override
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
      return GrIndexPropertyImpl.this;
    }

    @RequiredReadAction
    @Override
    public boolean isReferenceTo(PsiElement element) {
      return getManager().areElementsEquivalent(resolve(), element);
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @RequiredReadAction
    @Override
    public boolean isSoft() {
      return false;
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return GrIndexPropertyImpl.this.multiResolveGroovy(incompleteCode);
    }
  }
}
