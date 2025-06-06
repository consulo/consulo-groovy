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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.util.RecursionManager;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeInsight.GroovyTargetElementUtilEx;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;

import java.util.*;
import java.util.function.Function;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl<GrExpression> implements GrReferenceExpression {
    private static final Logger LOG = Logger.getInstance(GrReferenceExpressionImpl.class);

    public GrReferenceExpressionImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @RequiredReadAction
    private boolean findClassOrPackageAtFirst() {
        final String name = getReferenceName();
        //noinspection SimplifiableIfStatement
        if (StringUtil.isEmpty(name) || hasAt()) {
            return false;
        }

        return Character.isUpperCase(name.charAt(0)) && !isMethodCallRef()
            || getParent() instanceof GrReferenceExpressionImpl refExpr && refExpr.findClassOrPackageAtFirst();
    }

    private boolean isMethodCallRef() {
        final PsiElement parent = getParent();
        return parent instanceof GrMethodCall || parent instanceof GrReferenceExpressionImpl refExpr && refExpr.isMethodCallRef();
    }

    private boolean isDefinitelyKeyOfMap() {
        final GrExpression qualifier = ResolveUtil.getSelfOrWithQualifier(this);
        if (qualifier == null) {
            return false;
        }
        //key in 'java.util.Map.key' is not access to map, it is access to static property of field
        if (qualifier instanceof GrReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass) {
            return false;
        }

        final PsiType type = qualifier.getType();
        if (type == null) {
            return false;
        }

        if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
            return false;
        }

        final String qname = TypesUtil.getQualifiedName(type);
        if (qname != null) {
            if (qname.startsWith("java.")) {
                return true; //so we have jdk map here
            }
            if (GroovyCommonClassNames.GROOVY_UTIL_CONFIG_OBJECT.equals(qname)) {
                return false;
            }
            if (qname.startsWith("groovy.")) {
                return true; //we have gdk map here
            }
        }

        return false;
    }

    @Nonnull
    @RequiredReadAction
    private GroovyResolveResult[] resolveTypeOrProperty() {
        if (isDefinitelyKeyOfMap()) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        final GroovyResolveResult[] results = resolveTypeOrPropertyInner();
        if (results.length == 0) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        if (!ResolveUtil.mayBeKeyOfMap(this)) {
            return results;
        }

        //filter out all members from super classes. We should return only accessible members from map classes
        List<GroovyResolveResult> filtered = new ArrayList<>();
        for (GroovyResolveResult result : results) {
            if (result.getElement() instanceof PsiMember member) {
                if (member.isPrivate()) {
                    continue;
                }
                final PsiClass containingClass = member.getContainingClass();
                if (containingClass != null) {
                    if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_MAP)) {
                        continue;
                    }
                    final String name = containingClass.getQualifiedName();
                    if (name != null && name.startsWith("java.")) {
                        continue;
                    }
                    if (containingClass.getLanguage() != GroovyLanguage.INSTANCE &&
                        !InheritanceUtil.isInheritor(containingClass, GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME)) {
                        continue;
                    }
                }
            }
            filtered.add(result);
        }

        return filtered.toArray(new GroovyResolveResult[filtered.size()]);
    }

    @Nonnull
    @RequiredReadAction
    private GroovyResolveResult[] resolveTypeOrPropertyInner() {
        PsiElement nameElement = getReferenceNameElement();
        String name = getReferenceName();

        if (name == null || nameElement == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        IElementType nameType = nameElement.getNode().getElementType();
        if (nameType == GroovyTokenTypes.kTHIS) {
            GroovyResolveResult[] results = GrThisReferenceResolver.resolveThisExpression(this);
            if (results != null) {
                return results;
            }
        }
        else if (nameType == GroovyTokenTypes.kSUPER) {
            GroovyResolveResult[] results = GrSuperReferenceResolver.resolveSuperExpression(this);
            if (results != null) {
                return results;
            }
        }


        EnumSet<ClassHint.ResolveKind> kinds = getParent() instanceof GrReferenceExpression
            ? ClassHint.RESOLVE_KINDS_CLASS_PACKAGE
            : ClassHint.RESOLVE_KINDS_CLASS;

        GroovyResolveResult[] classCandidates = null;

        GrReferenceResolveRunner resolveRunner = new GrReferenceResolveRunner(this);

        ResolverProcessorImpl processor = new PropertyResolverProcessor(name, this);
        resolveRunner.resolveImpl(processor);
        final GroovyResolveResult[] fieldCandidates = processor.getCandidates();

        if (hasAt()) {
            return fieldCandidates;
        }

        boolean canBeClassOrPackage = ResolveUtil.canBeClassOrPackage(this);

        if (canBeClassOrPackage && findClassOrPackageAtFirst()) {
            ResolverProcessorImpl classProcessor = new ClassResolverProcessor(name, this, kinds);
            resolveRunner.resolveImpl(classProcessor);
            classCandidates = classProcessor.getCandidates();
            if (classCandidates.length > 0 && containsPackage(classCandidates)) {
                return classCandidates;
            }
        }

        //if reference expression is in class we need to return field instead of accessor method
        for (GroovyResolveResult candidate : fieldCandidates) {
            final PsiElement element = candidate.getElement();
            if (element instanceof PsiField field) {
                final PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && PsiUtil.getContextClass(this) == containingClass) {
                    return fieldCandidates;
                }
            }
            else if (!(element instanceof GrBindingVariable)) {
                return fieldCandidates;
            }
        }

        if (classCandidates != null && classCandidates.length > 0) {
            return classCandidates;
        }

        final boolean isLValue = PsiUtil.isLValue(this);
        String[] accessorNames = isLValue ? GroovyPropertyUtils.suggestSettersName(name) : GroovyPropertyUtils.suggestGettersName(name);
        List<GroovyResolveResult> accessorResults = new ArrayList<>();
        for (String accessorName : accessorNames) {
            AccessorResolverProcessor accessorResolver = new AccessorResolverProcessor(
                accessorName,
                name,
                this,
                !isLValue,
                false,
                PsiImplUtil.getQualifierType(this),
                getTypeArguments()
            );
            resolveRunner.resolveImpl(accessorResolver);
            final GroovyResolveResult[] candidates = accessorResolver.getCandidates();

            //can be only one correct candidate or some incorrect
            if (candidates.length == 1 && candidates[0].isStaticsOK() && candidates[0].isAccessible()) {
                return candidates;
            }
            else {
                ContainerUtil.addAll(accessorResults, candidates);
            }
        }

        final ArrayList<GroovyResolveResult> fieldList = ContainerUtil.newArrayList(fieldCandidates);
        filterOutBindings(fieldList);
        if (!fieldList.isEmpty()) {
            return fieldList.toArray(new GroovyResolveResult[fieldList.size()]);
        }

        if (classCandidates == null && canBeClassOrPackage) {
            ResolverProcessorImpl classProcessor = new ClassResolverProcessor(name, this, kinds);
            resolveRunner.resolveImpl(classProcessor);
            classCandidates = classProcessor.getCandidates();
        }

        if (classCandidates != null && classCandidates.length > 0) {
            return classCandidates;
        }
        if (!accessorResults.isEmpty()) {
            return new GroovyResolveResult[]{accessorResults.get(0)};
        }
        return GroovyResolveResult.EMPTY_ARRAY;
    }

    private static boolean containsPackage(@Nonnull GroovyResolveResult[] candidates) {
        for (GroovyResolveResult candidate : candidates) {
            if (candidate.getElement() instanceof PsiPackage) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    @RequiredReadAction
    public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
        return resolveMethodOrProperty(true, upToArgument, true);
    }

    private void processMethods(@Nonnull MethodResolverProcessor methodResolver) {
        new GrReferenceResolveRunner(this).resolveImpl(methodResolver);
        if (methodResolver.hasApplicableCandidates()) {
            return;
        }

        // Search in ClosureMissingMethodContributor
        if (!isQualified() && getContext() instanceof GrMethodCall) {
            ClosureMissingMethodContributor.processMethodsFromClosures(this, methodResolver);
        }
    }

    /**
     * priority: inside class C: local variable, c.method, c.property, c.getter
     * in other places: local variable, c.method, c.getter, c.property
     */
    @Nonnull
    @RequiredReadAction
    private GroovyResolveResult[] resolveMethodOrProperty(
        boolean allVariants,
        @Nullable GrExpression upToArgument,
        boolean genericsMatter
    ) {
        final String name = getReferenceName();
        if (name == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        GrReferenceResolveRunner resolveRunner = new GrReferenceResolveRunner(this);

        PropertyResolverProcessor propertyResolver = new PropertyResolverProcessor(name, this);
        resolveRunner.resolveImpl(propertyResolver);
        final GroovyResolveResult[] propertyCandidates = propertyResolver.getCandidates();

        if (!allVariants) { //search for local variables
            for (GroovyResolveResult candidate : propertyCandidates) {
                final PsiElement element = candidate.getElement();
                if (element instanceof GrVariable && !(element instanceof GrField || element instanceof GrBindingVariable)) {
                    return propertyCandidates;
                }
            }
        }

        final Pair<Boolean, GroovyResolveResult[]> shapeResults = resolveByShape(allVariants, upToArgument);
        if (!genericsMatter && !allVariants && shapeResults.first) {
            assertAllAreValid(shapeResults.second);
            return shapeResults.second;
        }

        MethodResolverProcessor methodResolver = null;
        if (genericsMatter) {
            methodResolver = createMethodProcessor(allVariants, name, false, upToArgument);

            for (GroovyResolveResult result : shapeResults.second) {
                final ResolveState state = ResolveState.initial()
                    .put(PsiSubstitutor.KEY, result.getSubstitutor())
                    .put(ClassHint.RESOLVE_CONTEXT, result.getCurrentFileResolveContext())
                    .put(SpreadState.SPREAD_STATE, result.getSpreadState());
                PsiElement element = result.getElement();
                assert element != null;
                methodResolver.execute(element, state);
            }

            if (!allVariants && methodResolver.hasApplicableCandidates()) {
                return methodResolver.getCandidates();
            }
        }

        //search for fields inside its class
        if (!allVariants) {
            for (GroovyResolveResult candidate : propertyCandidates) {
                if (candidate.getElement() instanceof GrField field) {
                    final PsiClass containingClass = field.getContainingClass();
                    if (containingClass != null && PsiTreeUtil.isContextAncestor(containingClass, this, true)) {
                        return propertyCandidates;
                    }
                }
            }
        }

        List<GroovyResolveResult> allCandidates = new ArrayList<>();
        ContainerUtil.addAll(allCandidates, propertyCandidates);
        ContainerUtil.addAll(allCandidates, genericsMatter ? methodResolver.getCandidates() : shapeResults.second);

        filterOutBindings(allCandidates);

        //search for getters
        for (String getterName : GroovyPropertyUtils.suggestGettersName(name)) {
            AccessorResolverProcessor getterResolver = new AccessorResolverProcessor(
                getterName,
                name,
                this,
                true,
                genericsMatter,
                PsiImplUtil.getQualifierType(this),
                getTypeArguments()
            );
            resolveRunner.resolveImpl(getterResolver);
            final GroovyResolveResult[] candidates = getterResolver.getCandidates(); //can be only one candidate
            if (!allVariants && candidates.length == 1) {
                return candidates;
            }
            ContainerUtil.addAll(allCandidates, candidates);
        }

        if (!allCandidates.isEmpty()) {
            return allCandidates.toArray(new GroovyResolveResult[allCandidates.size()]);
        }
        return GroovyResolveResult.EMPTY_ARRAY;
    }

    private static void filterOutBindings(@Nonnull List<GroovyResolveResult> candidates) {
        boolean hasNonBinding = false;
        for (GroovyResolveResult candidate : candidates) {
            if (!(candidate.getElement() instanceof GrBindingVariable)) {
                hasNonBinding = true;
            }
        }

        if (hasNonBinding) {
            for (Iterator<GroovyResolveResult> iterator = candidates.iterator(); iterator.hasNext(); ) {
                GroovyResolveResult candidate = iterator.next();
                if (candidate.getElement() instanceof GrBindingVariable) {
                    iterator.remove();
                }
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    private Pair<Boolean, GroovyResolveResult[]> resolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
        if (allVariants) {
            return doResolveByShape(true, upToArgument);
        }

        LOG.assertTrue(upToArgument == null);

        //noinspection RequiredXAction
        return TypeInferenceHelper.getCurrentContext()
            .getCachedValue(this, () -> doResolveByShape(false, null));
    }

    @Nonnull
    @RequiredReadAction
    private Pair<Boolean, GroovyResolveResult[]> doResolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
        final String name = getReferenceName();
        LOG.assertTrue(name != null);

        final MethodResolverProcessor shapeProcessor = createMethodProcessor(allVariants, name, true, upToArgument);
        processMethods(shapeProcessor);
        GroovyResolveResult[] candidates = shapeProcessor.getCandidates();
        assertAllAreValid(candidates);

        if (hasMemberPointer()) {
            candidates = collapseReflectedMethods(candidates);
        }

        return Pair.create(shapeProcessor.hasApplicableCandidates(), candidates);
    }

    @Nonnull
    private static GroovyResolveResult[] collapseReflectedMethods(GroovyResolveResult[] candidates) {
        Set<GrMethod> visited = new HashSet<>();
        List<GroovyResolveResult> collapsed = new ArrayList<>();
        for (GroovyResolveResult result : candidates) {
            if (result.getElement() instanceof GrReflectedMethod reflectedMethod) {
                GrMethod baseMethod = reflectedMethod.getBaseMethod();
                if (visited.add(baseMethod)) {
                    collapsed.add(PsiImplUtil.reflectedToBase(result, baseMethod, reflectedMethod));
                }
            }
            else {
                collapsed.add(result);
            }
        }
        return collapsed.toArray(new GroovyResolveResult[collapsed.size()]);
    }

    private static void assertAllAreValid(@Nonnull GroovyResolveResult[] candidates) {
        for (GroovyResolveResult candidate : candidates) {
            final PsiElement element = candidate.getElement();
            LOG.assertTrue(element == null || element.isValid());
        }
    }

    @Nonnull
    private MethodResolverProcessor createMethodProcessor(
        boolean allVariants,
        @Nullable String name,
        final boolean byShape,
        @Nullable GrExpression upToArgument
    ) {
        final PsiType[] argTypes = PsiUtil.getArgumentTypes(this, false, upToArgument, byShape);
        if (byShape && argTypes != null) {
            for (int i = 0; i < argTypes.length; i++) {
                argTypes[i] = TypeConversionUtil.erasure(argTypes[i]);
            }
        }
        PsiType qualifierType = PsiImplUtil.getQualifierType(this);
        return new MethodResolverProcessor(name, this, false, qualifierType, argTypes, getTypeArguments(), allVariants, byShape);
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
        visitor.visitReferenceExpression(this);
    }

    @Override
    @Nullable
    public PsiElement getReferenceNameElement() {
        final ASTNode lastChild = getNode().getLastChildNode();
        if (lastChild == null) {
            return null;
        }
        if (TokenSets.REFERENCE_NAMES.contains(lastChild.getElementType())) {
            return lastChild.getPsi();
        }

        return null;
    }

    @Override
    @Nonnull
    public PsiReference getReference() {
        return this;
    }

    @Override
    @Nullable
    public GrExpression getQualifier() {
        return getQualifierExpression();
    }

    @Override
    @Nullable
    @RequiredReadAction
    public String getReferenceName() {
        PsiElement nameElement = getReferenceNameElement();
        if (nameElement != null) {
            IElementType nodeType = nameElement.getNode().getElementType();
            return TokenSets.STRING_LITERAL_SET.contains(nodeType)
                && GrLiteralImpl.getLiteralValue(nameElement) instanceof String strValue
                ? strValue
                : nameElement.getText();

        }
        return null;
    }

    @Override
    @RequiredWriteAction
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        final GroovyResolveResult result = advancedResolve();
        if (result.isInvokedOnProperty()) {
            final String name = GroovyPropertyUtils.getPropertyNameByAccessorName(newElementName);
            if (name != null) {
                newElementName = name;
            }
        }
        if (PsiUtil.isThisOrSuperRef(this)) {
            return this;
        }

        return handleElementRenameSimple(newElementName);
    }

    @Override
    protected GrReferenceExpression bindWithQualifiedRef(@Nonnull String qName) {
        GrReferenceExpression qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createReferenceExpressionFromText(qName);
        final GrTypeArgumentList list = getTypeArgumentList();
        if (list != null) {
            qualifiedRef.getNode().addChild(list.copy().getNode());
        }
        getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
        return qualifiedRef;
    }

    @Override
    @RequiredReadAction
    public boolean isFullyQualified() {
        return getKind() == Kind.TYPE_OR_PROPERTY && resolve() instanceof PsiPackage
            || getQualifier() instanceof GrReferenceExpressionImpl refExpr && refExpr.isFullyQualified();
    }

    @Override
    public PsiElement handleElementRenameSimple(String newElementName) throws IncorrectOperationException {
        if (!PsiUtil.isValidReferenceName(newElementName)) {
            final PsiElement old = getReferenceNameElement();
            if (old == null) {
                throw new IncorrectOperationException("ref has no name element");
            }

            PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteralForReference(newElementName);
            old.replace(element);
            return this;
        }

        return super.handleElementRenameSimple(newElementName);
    }

    @Override
    public String toString() {
        return "Reference expression";
    }

    @Override
    @Nullable
    @RequiredReadAction
    public PsiElement resolve() {
        final GroovyResolveResult[] results = resolveByShape();
        return results.length == 1 ? results[0].getElement() : null;
    }

    @Override
    @RequiredReadAction
    public GroovyResolveResult[] resolveByShape() {
        final InferenceContext context = TypeInferenceHelper.getCurrentContext();
        return context.getCachedValue(
            this,
            () -> {
                Pair<GrReferenceExpressionImpl, InferenceContext> key = Pair.create(GrReferenceExpressionImpl.this, context);
                @SuppressWarnings("RequiredXAction")
                GroovyResolveResult[] value = RecursionManager.doPreventingRecursion(
                    key,
                    true,
                    () -> doPolyResolve(false, false)
                );
                return value == null ? GroovyResolveResult.EMPTY_ARRAY : value;
            }
        );
    }

    private static final ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> POLY_RESOLVER =
        new ResolveCache.PolyVariantResolver<>() {
            @Override
            @Nonnull
            @RequiredReadAction
            public GroovyResolveResult[] resolve(@Nonnull GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
                return refExpr.doPolyResolve(incompleteCode, true);
            }
        };
    private static final OurTypesCalculator TYPES_CALCULATOR = new OurTypesCalculator();

    @Override
    @Nullable
    @RequiredReadAction
    public PsiType getNominalType() {
        final GroovyResolveResult resolveResult = advancedResolve();
        PsiElement resolved = resolveResult.getElement();

        for (GrReferenceTypeEnhancer enhancer : GrReferenceTypeEnhancer.EP_NAME.getExtensions()) {
            PsiType type = enhancer.getReferenceType(this, resolved);
            if (type != null) {
                return type;
            }
        }

        IElementType dotType = getDotTokenType();
        if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
            return GrClosureType.create(multiResolve(false), this);
        }

        if (isDefinitelyKeyOfMap()) {
            final PsiType type = getTypeFromMapAccess(this);
            if (type != null) {
                return type;
            }
        }

        PsiType result = getNominalTypeInner(resolved);
        if (result == null) {
            return null;
        }

        result = TypesUtil.substituteAndNormalizeType(result, resolveResult.getSubstitutor(), resolveResult.getSpreadState(), this);
        return result;
    }

    @Nullable
    @RequiredReadAction
    private PsiType getNominalTypeInner(@Nullable PsiElement resolved) {
        if (resolved == null && !"class".equals(getReferenceName())) {
            resolved = resolve();
        }

        if (resolved instanceof PsiClass psiClass) {
            final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
            if (PsiUtil.isInstanceThisRef(this)) {
                final PsiClassType categoryType = GdkMethodUtil.getCategoryType(psiClass);
                return categoryType != null ? categoryType : factory.createType(psiClass);
            }
            else if (PsiUtil.isSuperReference(this)) {
                PsiClass contextClass = PsiUtil.getContextClass(this);
                if (GrTraitUtil.isTrait(contextClass)) {
                    PsiClassType[] extendsTypes = contextClass.getExtendsListTypes();
                    PsiClassType[] implementsTypes = contextClass.getImplementsListTypes();

                    PsiClassType[] superTypes = ArrayUtil.mergeArrays(implementsTypes, extendsTypes, PsiClassType.ARRAY_FACTORY);

                    return PsiIntersectionType.createIntersection(ArrayUtil.reverseArray(superTypes));
                }
                return factory.createType(psiClass);
            }
            if (getParent() instanceof GrReferenceExpression) {
                return factory.createType(psiClass);
            }
            else {
                return TypesUtil.createJavaLangClassType(factory.createType(psiClass), getProject(), getResolveScope());
            }
        }

        if (resolved instanceof GrVariable variable) {
            return variable.getDeclaredType();
        }

        if (resolved instanceof PsiVariable variable) {
            return variable.getType();
        }

        if (resolved instanceof PsiMethod method) {
            if (PropertyUtil.isSimplePropertySetter(method) && !method.getName().equals(getReferenceName())) {
                return method.getParameterList().getParameters()[0].getType();
            }

            //'class' property with explicit generic
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null
                && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())
                && "getClass".equals(method.getName())) {
                return TypesUtil.createJavaLangClassType(PsiImplUtil.getQualifierType(this), getProject(), getResolveScope());
            }

            return PsiUtil.getSmartReturnType(method);
        }

        if (resolved == null) {
            final PsiType fromClassRef = getTypeFromClassRef(this);
            if (fromClassRef != null) {
                return fromClassRef;
            }

            final PsiType fromMapAccess = getTypeFromMapAccess(this);
            if (fromMapAccess != null) {
                return fromMapAccess;
            }

            final PsiType fromSpreadOperator = getTypeFromSpreadOperator(this);
            if (fromSpreadOperator != null) {
                return fromSpreadOperator;
            }
        }

        return null;
    }

    @Nullable
    private static PsiType getTypeFromMapAccess(@Nonnull GrReferenceExpressionImpl ref) {
        //map access
        GrExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null) {
            PsiType qType = qualifier.getNominalType();
            if (qType instanceof PsiClassType qClassType) {
                PsiClassType.ClassResolveResult qResult = qClassType.resolveGenerics();
                PsiClass clazz = qResult.getElement();
                if (clazz != null) {
                    PsiClass mapClass =
                        JavaPsiFacade.getInstance(ref.getProject()).findClass(CommonClassNames.JAVA_UTIL_MAP, ref.getResolveScope());
                    if (mapClass != null && mapClass.getTypeParameters().length == 2) {
                        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(mapClass, clazz, qResult.getSubstitutor());
                        if (substitutor != null) {
                            PsiType substituted = substitutor.substitute(mapClass.getTypeParameters()[1]);
                            if (substituted != null) {
                                return PsiImplUtil.normalizeWildcardTypeByPosition(substituted, ref);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static PsiType getTypeFromSpreadOperator(@Nonnull GrReferenceExpressionImpl ref) {
        return ref.getDotTokenType() == GroovyTokenTypes.mSPREAD_DOT
            ? TypesUtil.createType(CommonClassNames.JAVA_UTIL_LIST, ref)
            : null;
    }

    @Nullable
    @RequiredReadAction
    private static PsiType getTypeFromClassRef(@Nonnull GrReferenceExpressionImpl ref) {
        if ("class".equals(ref.getReferenceName())) {
            return TypesUtil.createJavaLangClassType(PsiImplUtil.getQualifierType(ref), ref.getProject(), ref.getResolveScope());
        }
        return null;
    }

    private static final class OurTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
        @Nullable
        @Override
        @RequiredReadAction
        public PsiType apply(GrReferenceExpressionImpl refExpr) {
            if (ResolveUtil.isClassReference(refExpr)) {
                GrExpression qualifier = refExpr.getQualifier();
                LOG.assertTrue(qualifier != null);
                return TypesUtil.createJavaLangClassType(qualifier.getType(), refExpr.getProject(), refExpr.getResolveScope());
            }

            if (PsiUtil.isCompileStatic(refExpr)) {
                final GroovyResolveResult resolveResult = refExpr.advancedResolve();
                final PsiElement resolvedF = resolveResult.getElement();
                final PsiType type;
                if (resolvedF instanceof GrField field) {
                    type = field.getType();
                }
                else if (resolvedF instanceof GrAccessorMethod accessorMethod) {
                    type = accessorMethod.getProperty().getType();
                }
                else {
                    type = null;
                }
                if (type != null) {
                    return resolveResult.getSubstitutor().substitute(type);
                }
            }

            final PsiElement resolved = refExpr.resolve();
            final PsiType nominal = refExpr.getNominalType();

            Boolean reassigned = GrReassignedLocalVarsChecker.isReassignedVar(refExpr);
            if (reassigned != null && reassigned) {
                return GrReassignedLocalVarsChecker.getReassignedVarType(refExpr, true);
            }

            final PsiType inferred = getInferredTypes(refExpr, resolved);
            if (inferred == null) {
                if (nominal == null) {
                    //inside nested closure we could still try to infer from variable initializer. Not sound, but makes sense
                    if (resolved instanceof GrVariable variable) {
                        LOG.assertTrue(resolved.isValid());
                        return variable.getTypeGroovy();
                    }
                }

                return nominal;
            }

            if (nominal == null) {
                return inferred;
            }
            if (!TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(nominal), inferred, false)
                && resolved instanceof GrVariable variable && variable.getTypeElementGroovy() != null) {
                return nominal;
            }
            return inferred;
        }
    }

    @Nullable
    private static PsiType getInferredTypes(@Nonnull GrReferenceExpressionImpl refExpr, @Nullable PsiElement resolved) {
        final GrExpression qualifier = refExpr.getQualifier();
        if (qualifier == null && !(resolved instanceof PsiClass || resolved instanceof PsiPackage)) {
            return TypeInferenceHelper.getCurrentContext().getVariableType(refExpr);
        }
        else if (qualifier != null) {
            //map access
            PsiType qType = qualifier.getType();
            if (qType instanceof PsiClassType && !(qType instanceof GrMapType)) {
                final PsiType mapValueType = getTypeFromMapAccess(refExpr);
                if (mapValueType != null) {
                    return mapValueType;
                }
            }
        }
        return null;
    }

    @Override
    public PsiType getType() {
        return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
    }

    @Override
    public GrExpression replaceWithExpression(@Nonnull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
        return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
    }

    @Nonnull
    @RequiredReadAction
    private GroovyResolveResult[] doPolyResolve(boolean incompleteCode, boolean genericsMatter) {
        String name = getReferenceName();
        if (name == null) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        if (incompleteCode) {
            ResolverProcessorImpl processor = CompletionProcessor.createRefSameNameProcessor(this, name);
            new GrReferenceResolveRunner(this).resolveImpl(processor);
            GroovyResolveResult[] propertyCandidates = processor.getCandidates();
            if (propertyCandidates.length > 0 && !PsiUtil.isSingleBindingVariant(propertyCandidates)) {
                return propertyCandidates;
            }
        }

        try {
            //ResolveProfiler.start();
            switch (getKind()) {
                case METHOD_OR_PROPERTY:
                    return resolveMethodOrProperty(false, null, genericsMatter);
                case TYPE_OR_PROPERTY:
                    return resolveTypeOrProperty();
                case METHOD_OR_PROPERTY_OR_TYPE:
                    GroovyResolveResult[] results = resolveMethodOrProperty(false, null, genericsMatter);
                    if (results.length == 0) {
                        results = resolveTypeOrProperty();
                    }
                    return results;
                default:
                    return GroovyResolveResult.EMPTY_ARRAY;
            }
        }
        finally {
            //final long time = ResolveProfiler.finish();
            //ResolveProfiler.write("ref", this, time);
        }
    }

    enum Kind {
        TYPE_OR_PROPERTY,
        METHOD_OR_PROPERTY,
        METHOD_OR_PROPERTY_OR_TYPE
    }

    @Nonnull
    @RequiredReadAction
    private Kind getKind() {
        if (hasMemberPointer()) {
            return Kind.METHOD_OR_PROPERTY;
        }

        PsiElement parent = getParent();
        if (parent instanceof GrMethodCallExpression || parent instanceof GrApplicationStatement) {
            return Kind.METHOD_OR_PROPERTY_OR_TYPE;
        }

        return Kind.TYPE_OR_PROPERTY;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public String getCanonicalText() {
        return getRangeInElement().substring(getElement().getText());
    }

    @Override
    @RequiredReadAction
    public boolean hasAt() {
        return findChildByType(GroovyTokenTypes.mAT) != null;
    }

    @Override
    @RequiredReadAction
    public boolean hasMemberPointer() {
        return findChildByType(GroovyTokenTypes.mMEMBER_POINTER) != null;
    }

    @Override
    @RequiredReadAction
    public boolean isReferenceTo(PsiElement element) {
        PsiElement baseTarget = resolve();
        if (getManager().areElementsEquivalent(element, baseTarget)) {
            return true;
        }

        PsiElement target = GroovyTargetElementUtilEx.correctSearchTargets(baseTarget);
        if (target != baseTarget && getManager().areElementsEquivalent(element, target)) {
            return true;
        }

        if (element instanceof PsiMethod && target instanceof PsiMethod targetMethod) {
            PsiMethod[] superMethods = targetMethod.findSuperMethods(false);
            //noinspection SuspiciousMethodCalls
            if (Arrays.asList(superMethods).contains(element)) {
                return true;
            }
        }

        return false;
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

    @Override
    @Nullable
    public GrExpression getQualifierExpression() {
        return GroovyPsiElementImpl.findExpressionChild(this);
    }

    @Override
    @Nullable
    @RequiredReadAction
    public PsiElement getDotToken() {
        return findChildByType(TokenSets.DOTS);
    }

    @Override
    @RequiredReadAction
    public void replaceDotToken(PsiElement newDot) {
        if (newDot == null) {
            return;
        }
        if (!TokenSets.DOTS.contains(newDot.getNode().getElementType())) {
            return;
        }
        PsiElement oldDot = getDotToken();
        if (oldDot == null) {
            return;
        }

        getNode().replaceChild(oldDot.getNode(), newDot.getNode());
    }

    @Override
    @Nullable
    @RequiredReadAction
    public IElementType getDotTokenType() {
        PsiElement dot = getDotToken();
        return dot == null ? null : dot.getNode().getElementType();
    }

    @Override
    public GroovyResolveResult advancedResolve() {
        ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, false, POLY_RESOLVER);
        return results.length == 1 ? (GroovyResolveResult)results[0] : GroovyResolveResult.EMPTY_RESULT;
    }

    @Override
    @Nonnull
    public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
        final ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, incomplete, POLY_RESOLVER);
        return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public GroovyResolveResult[] getSameNameVariants() {
        return doPolyResolve(true, true);
    }

    @Override
    @RequiredReadAction
    public GrReferenceExpression bindToElementViaStaticImport(@Nonnull PsiMember member) {
        if (getQualifier() != null) {
            throw new IncorrectOperationException("Reference has qualifier");
        }

        if (StringUtil.isEmpty(getReferenceName())) {
            throw new IncorrectOperationException("Reference has empty name");
        }

        PsiClass containingClass = member.getContainingClass();
        if (containingClass == null) {
            throw new IncorrectOperationException("Member has no containing class");
        }
        if (getContainingFile() instanceof GroovyFile groovyFile) {
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
            String text = "import static " + containingClass.getQualifiedName() + "." + member.getName();
            final GrImportStatement statement = factory.createImportStatementFromText(text);
            groovyFile.addImport(statement);
        }
        return this;
    }
}
