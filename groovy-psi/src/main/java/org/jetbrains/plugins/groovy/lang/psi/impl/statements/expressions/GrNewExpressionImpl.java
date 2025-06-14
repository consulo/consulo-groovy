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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnonymousClassType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author ilyas
 */
public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {
    private static final Function<GrNewExpressionImpl, PsiType> MY_TYPE_CALCULATOR = newExpression -> {
        final GrAnonymousClassDefinition anonymous = newExpression.getAnonymousClassDefinition();
        if (anonymous != null) {
            return new GrAnonymousClassType(
                LanguageLevel.JDK_1_5,
                anonymous.getResolveScope(),
                JavaPsiFacade.getInstance(newExpression.getProject()),
                anonymous
            );
        }
        PsiType type = null;
        GrCodeReferenceElement refElement = newExpression.getReferenceElement();
        if (refElement != null) {
            type = new GrClassReferenceType(refElement);
        }
        else {
            GrBuiltInTypeElement builtin = newExpression.findChildByClass(GrBuiltInTypeElement.class);
            if (builtin != null) {
                type = builtin.getType();
            }
        }

        if (type != null) {
            for (int i = 0; i < newExpression.getArrayCount(); i++) {
                type = type.createArrayType();
            }
            return type;
        }

        return null;
    };

    private static final ResolveCache.PolyVariantResolver<MyFakeReference> RESOLVER = new ResolveCache.PolyVariantResolver<>() {
        @Nonnull
        @Override
        @RequiredReadAction
        public GroovyResolveResult[] resolve(@Nonnull MyFakeReference reference, boolean incompleteCode) {
            return reference.getElement().resolveImpl(incompleteCode);
        }
    };

    private final MyFakeReference myFakeReference = new MyFakeReference();

    public GrNewExpressionImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NEW expression";
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
        visitor.visitNewExpression(this);
    }

    @Override
    public PsiType getType() {
        return TypeInferenceHelper.getCurrentContext().getExpressionType(this, MY_TYPE_CALCULATOR);
    }

    @Override
    @RequiredWriteAction
    public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
        final GrArgumentList list = getArgumentList();
        if (list == null) { //so it is not anonymous class declaration
            final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
            final GrArgumentList newList = factory.createExpressionArgumentList();
            PsiElement last = getLastChild();
            assert last != null;
            while (last.getPrevSibling() instanceof PsiWhiteSpace || last.getPrevSibling() instanceof PsiErrorElement) {
                last = last.getPrevSibling();
                assert last != null;
            }
            ASTNode astNode = last.getNode();
            assert astNode != null;
            getNode().addChild(newList.getNode(), astNode);
        }
        return super.addNamedArgument(namedArgument);
    }

    @Override
    @RequiredReadAction
    public GrArgumentList getArgumentList() {
        final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
        if (anonymous != null) {
            return anonymous.getArgumentListGroovy();
        }
        return super.getArgumentList();
    }

    @Override
    @Nullable
    @RequiredReadAction
    public GrExpression getQualifier() {
        final PsiElement[] children = getChildren();
        for (PsiElement child : children) {
            if (child instanceof GrExpression expression) {
                return expression;
            }
            if (PsiKeyword.NEW.equals(child.getText())) {
                return null;
            }
        }
        return null;
    }

    @Override
    public GrCodeReferenceElement getReferenceElement() {
        final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
        if (anonymous != null) {
            return anonymous.getBaseClassReferenceGroovy();
        }
        return findChildByClass(GrCodeReferenceElement.class);
    }

    @Override
    public GroovyResolveResult[] multiResolveClass() {
        final GrCodeReferenceElement referenceElement = getReferenceElement();
        if (referenceElement != null) {
            return referenceElement.multiResolve(false);
        }
        return GroovyResolveResult.EMPTY_ARRAY;
    }

    @Override
    public int getArrayCount() {
        final GrArrayDeclaration arrayDeclaration = getArrayDeclaration();
        if (arrayDeclaration == null) {
            return 0;
        }
        return arrayDeclaration.getArrayCount();
    }

    @Override
    public GrAnonymousClassDefinition getAnonymousClassDefinition() {
        return findChildByClass(GrAnonymousClassDefinition.class);
    }

    @Nullable
    @Override
    public GrArrayDeclaration getArrayDeclaration() {
        return findChildByClass(GrArrayDeclaration.class);
    }

    @Nullable
    @Override
    public GrTypeArgumentList getConstructorTypeArguments() {
        return findChildByClass(GrTypeArgumentList.class);
    }

    @Override
    @Nullable
    public PsiMethod resolveMethod() {
        return PsiImplUtil.extractUniqueElement(multiResolveGroovy(false));
    }

    @Nonnull
    @Override
    public GroovyResolveResult advancedResolve() {
        return PsiImplUtil.extractUniqueResult(multiResolveGroovy(false));
    }

    @Override
    @Nonnull
    public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
        final GrCodeReferenceElement referenceElement = getReferenceElement();
        if (referenceElement == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        List<GroovyResolveResult> result = new ArrayList<>();
        for (GroovyResolveResult classResult : referenceElement.multiResolve(false)) {
            final PsiElement element = classResult.getElement();
            if (element instanceof PsiClass psiClass) {
                ContainerUtil.addAll(
                    result,
                    ResolveUtil.getAllClassConstructors(psiClass, classResult.getSubstitutor(), null, this)
                );
            }
        }

        return result.toArray(new GroovyResolveResult[result.size()]);
    }

    @Override
    public GrTypeElement getTypeElement() {
        return findChildByClass(GrTypeElement.class);
    }

    @Nonnull
    @Override
    public GroovyResolveResult[] multiResolveGroovy(boolean incompleteCode) {
        if (getArrayCount() > 0 || getReferenceElement() == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        return TypeInferenceHelper.getCurrentContext().multiResolve(myFakeReference, incompleteCode, RESOLVER);
    }

    @RequiredReadAction
    private GroovyResolveResult[] resolveImpl(boolean incompleteCode) {
        GrCodeReferenceElement ref = getReferenceElement();
        if (ref == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        GroovyResolveResult classCandidate = inferClassCandidate(ref);
        if (classCandidate == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }
        assert classCandidate.getElement() instanceof PsiClass;

        if (incompleteCode) {
            return PsiUtil.getConstructorCandidates(ref, classCandidate, null);
        }

        final GrArgumentList argumentList = getArgumentList();
        if (argumentList == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        if (argumentList.getNamedArguments().length > 0 && argumentList.getExpressionArguments().length == 0) {
            PsiType mapType = GrMapType.createFromNamedArgs(argumentList, getNamedArguments());
            GroovyResolveResult[] constructorResults =
                PsiUtil.getConstructorCandidates(ref, classCandidate, new PsiType[]{mapType}); //one Map parameter, actually
            for (GroovyResolveResult result : constructorResults) {
                final PsiElement resolved = result.getElement();
                if (resolved instanceof PsiMethod constructor) {
                    final PsiParameter[] parameters = constructor.getParameterList().getParameters();
                    if (parameters.length == 1 && InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) {
                        return constructorResults;
                    }
                }
            }
            final GroovyResolveResult[] emptyConstructors = PsiUtil.getConstructorCandidates(ref, classCandidate, PsiType.EMPTY_ARRAY);
            if (emptyConstructors.length > 0) {
                return emptyConstructors;
            }
        }

        PsiType[] types = PsiUtil.getArgumentTypes(ref, true);

        if (types != null) {
            types = GrInnerClassConstructorUtil.addEnclosingArgIfNeeded(types, this, (PsiClass)classCandidate.getElement());
        }
        return PsiUtil.getConstructorCandidates(ref, classCandidate, types);
    }

    @Nullable
    private static GroovyResolveResult inferClassCandidate(@Nonnull GrCodeReferenceElement ref) {
        final GroovyResolveResult[] classResults = ref.multiResolve(false);
        for (GroovyResolveResult result : classResults) {
            if (result.getElement() instanceof PsiClass) {
                return result;
            }
        }
        return null;
    }

    private class MyFakeReference implements PsiPolyVariantReference {
        @Nonnull
        @Override
        @RequiredReadAction
        public ResolveResult[] multiResolve(boolean incompleteCode) {
            return GrNewExpressionImpl.this.multiResolveGroovy(incompleteCode);
        }

        @Override
        @RequiredReadAction
        public GrNewExpressionImpl getElement() {
            return GrNewExpressionImpl.this;
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public TextRange getRangeInElement() {
            return TextRange.EMPTY_RANGE;
        }

        @Nullable
        @Override
        @RequiredReadAction
        public PsiElement resolve() {
            return resolveMethod();
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public String getCanonicalText() {
            return "new expression";
        }

        @Override
        @RequiredWriteAction
        public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
            throw new UnsupportedOperationException("unsupported!");
        }

        @Override
        @RequiredWriteAction
        public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
            throw new UnsupportedOperationException("unsupported!");
        }

        @Override
        @RequiredReadAction
        public boolean isReferenceTo(PsiElement element) {
            return getManager().areElementsEquivalent(element, resolve());
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public Object[] getVariants() {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        @Override
        @RequiredReadAction
        public boolean isSoft() {
            return false;
        }
    }
}
