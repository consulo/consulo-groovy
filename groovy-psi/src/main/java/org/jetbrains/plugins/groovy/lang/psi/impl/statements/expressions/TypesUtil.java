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
import com.intellij.java.language.impl.psi.PsiTypeVisitorEx;
import com.intellij.java.language.impl.psi.impl.PsiSubstitutorImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.ComparatorUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrImmediateClosureSignatureImpl;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author ven
 */
public class TypesUtil {
    public static final Map<String, PsiType> ourQNameToUnboxed = new HashMap<>();
    public static final PsiPrimitiveType[] PRIMITIVES = {
        PsiType.BYTE,
        PsiType.CHAR,
        PsiType.DOUBLE,
        PsiType.FLOAT,
        PsiType.INT,
        PsiType.SHORT,
        PsiType.LONG,
        PsiType.BOOLEAN,
        PsiType.VOID
    };

    private TypesUtil() {
    }

    @Nonnull
    public static GroovyResolveResult[] getOverloadedOperatorCandidates(
        @Nonnull PsiType thisType,
        IElementType tokenType,
        @Nonnull GroovyPsiElement place,
        PsiType[] argumentTypes
    ) {
        return getOverloadedOperatorCandidates(thisType, tokenType, place, argumentTypes, false);
    }

    @Nonnull
    public static GroovyResolveResult[] getOverloadedOperatorCandidates(
        @Nonnull PsiType thisType,
        IElementType tokenType,
        @Nonnull GroovyPsiElement place,
        PsiType[] argumentTypes,
        boolean incompleteCode
    ) {
        return ResolveUtil.getMethodCandidates(
            thisType,
            ourOperationsToOperatorNames.get(tokenType),
            place,
            true,
            incompleteCode,
            false,
            argumentTypes
        );
    }


    public static GroovyResolveResult[] getOverloadedUnaryOperatorCandidates(
        @Nonnull PsiType thisType,
        IElementType tokenType,
        @Nonnull GroovyPsiElement place,
        PsiType[] argumentTypes
    ) {
        return ResolveUtil.getMethodCandidates(thisType, ourUnaryOperationsToOperatorNames.get(tokenType), place, argumentTypes);
    }

    private static final Map<IElementType, String> ourPrimitiveTypesToClassNames = new HashMap<>();
    private static final String NULL = "null";

    static {
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mSTRING_LITERAL, JavaClassNames.JAVA_LANG_STRING);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mGSTRING_LITERAL, JavaClassNames.JAVA_LANG_STRING);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mREGEX_LITERAL, JavaClassNames.JAVA_LANG_STRING);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL, JavaClassNames.JAVA_LANG_STRING);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_INT, JavaClassNames.JAVA_LANG_INTEGER);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_LONG, JavaClassNames.JAVA_LANG_LONG);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_FLOAT, JavaClassNames.JAVA_LANG_FLOAT);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_DOUBLE, JavaClassNames.JAVA_LANG_DOUBLE);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_BIG_INT, GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_BIG_DECIMAL, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kFALSE, JavaClassNames.JAVA_LANG_BOOLEAN);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kTRUE, JavaClassNames.JAVA_LANG_BOOLEAN);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kNULL, NULL);

        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kINT, JavaClassNames.JAVA_LANG_INTEGER);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kLONG, JavaClassNames.JAVA_LANG_LONG);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kFLOAT, JavaClassNames.JAVA_LANG_FLOAT);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kDOUBLE, JavaClassNames.JAVA_LANG_DOUBLE);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kBOOLEAN, JavaClassNames.JAVA_LANG_BOOLEAN);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kCHAR, JavaClassNames.JAVA_LANG_CHARACTER);
        ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kBYTE, JavaClassNames.JAVA_LANG_BYTE);
    }

    private static final Map<IElementType, String> ourOperationsToOperatorNames = new HashMap<>();
    private static final Map<IElementType, String> ourUnaryOperationsToOperatorNames = new HashMap<>();

    static {
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mPLUS, "plus");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mMINUS, "minus");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mBAND, "and");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mBOR, "or");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mBXOR, "xor");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mDIV, "div");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mMOD, "mod");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mSTAR, "multiply");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.kAS, "asType");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mCOMPARE_TO, "compareTo");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mGT, "compareTo");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mGE, "compareTo");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mLT, "compareTo");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mLE, "compareTo");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mSTAR_STAR, "power");
        ourOperationsToOperatorNames.put(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, "leftShift");
        ourOperationsToOperatorNames.put(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, "rightShift");
        ourOperationsToOperatorNames.put(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN, "rightShiftUnsigned");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mEQUAL, "equals");
        ourOperationsToOperatorNames.put(GroovyTokenTypes.mNOT_EQUAL, "equals");

        ourUnaryOperationsToOperatorNames.put(GroovyTokenTypes.mLNOT, "asBoolean");
        ourUnaryOperationsToOperatorNames.put(GroovyTokenTypes.mPLUS, "positive");
        ourUnaryOperationsToOperatorNames.put(GroovyTokenTypes.mMINUS, "negative");
        ourUnaryOperationsToOperatorNames.put(GroovyTokenTypes.mDEC, "previous");
        ourUnaryOperationsToOperatorNames.put(GroovyTokenTypes.mINC, "next");
        ourUnaryOperationsToOperatorNames.put(GroovyTokenTypes.mBNOT, "bitwiseNegate");
    }

    private static final ObjectIntMap<String> TYPE_TO_RANK = ObjectMaps.newObjectIntHashMap();

    static {
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_BYTE, 1);
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_SHORT, 2);
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_INTEGER, 3);
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_LONG, 4);
        TYPE_TO_RANK.putInt(GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER, 5);
        TYPE_TO_RANK.putInt(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL, 6);
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_FLOAT, 7);
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_DOUBLE, 8);
        TYPE_TO_RANK.putInt(JavaClassNames.JAVA_LANG_NUMBER, 9);
    }

    static {
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_BOOLEAN, PsiType.BOOLEAN);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_BYTE, PsiType.BYTE);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_CHARACTER, PsiType.CHAR);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_SHORT, PsiType.SHORT);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_INTEGER, PsiType.INT);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_LONG, PsiType.LONG);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_FLOAT, PsiType.FLOAT);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_DOUBLE, PsiType.DOUBLE);
        ourQNameToUnboxed.put(JavaClassNames.JAVA_LANG_VOID, PsiType.VOID);
    }

    private static final IntObjectMap<String> RANK_TO_TYPE = IntMaps.newIntObjectHashMap();

    static {
        RANK_TO_TYPE.put(1, JavaClassNames.JAVA_LANG_INTEGER);
        RANK_TO_TYPE.put(2, JavaClassNames.JAVA_LANG_INTEGER);
        RANK_TO_TYPE.put(3, JavaClassNames.JAVA_LANG_INTEGER);
        RANK_TO_TYPE.put(4, JavaClassNames.JAVA_LANG_LONG);
        RANK_TO_TYPE.put(5, GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER);
        RANK_TO_TYPE.put(6, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
        RANK_TO_TYPE.put(7, JavaClassNames.JAVA_LANG_DOUBLE);
        RANK_TO_TYPE.put(8, JavaClassNames.JAVA_LANG_DOUBLE);
        RANK_TO_TYPE.put(9, JavaClassNames.JAVA_LANG_NUMBER);
    }

    /**
     * @deprecated see {@link #canAssign}
     */
    @Deprecated
    public static boolean isAssignable(@Nullable PsiType lType, @Nullable PsiType rType, @Nonnull PsiElement context) {
        return lType != null && rType != null && canAssign(lType, rType, context, ApplicableTo.ASSIGNMENT) == ConversionResult.OK;
    }

    @Nonnull
    public static ConversionResult canAssign(
        @Nonnull PsiType targetType,
        @Nonnull PsiType actualType,
        @Nonnull PsiElement context,
        @Nonnull ApplicableTo position
    ) {
        if (actualType instanceof PsiIntersectionType) {
            ConversionResult min = ConversionResult.ERROR;
            for (PsiType child : ((PsiIntersectionType)actualType).getConjuncts()) {
                final ConversionResult result = canAssign(targetType, child, context, position);
                if (result.ordinal() < min.ordinal()) {
                    min = result;
                }
                if (min == ConversionResult.OK) {
                    return ConversionResult.OK;
                }
            }
            return min;
        }

        if (targetType instanceof PsiIntersectionType) {
            ConversionResult max = ConversionResult.OK;
            for (PsiType child : ((PsiIntersectionType)targetType).getConjuncts()) {
                final ConversionResult result = canAssign(child, actualType, context, position);
                if (result.ordinal() > max.ordinal()) {
                    max = result;
                }
                if (max == ConversionResult.ERROR) {
                    return ConversionResult.ERROR;
                }
            }
            return max;
        }

        final ConversionResult result = areTypesConvertible(targetType, actualType, context, position);
        if (result != null) {
            return result;
        }

        if (isAssignableWithoutConversions(targetType, actualType, context)) {
            return ConversionResult.OK;
        }

        final PsiManager manager = context.getManager();
        final GlobalSearchScope scope = context.getResolveScope();
        targetType = boxPrimitiveType(targetType, manager, scope);
        actualType = boxPrimitiveType(actualType, manager, scope);

        if (targetType.isAssignableFrom(actualType)) {
            return ConversionResult.OK;
        }

        return ConversionResult.ERROR;
    }

    public static boolean isAssignableByMethodCallConversion(
        @Nullable PsiType targetType,
        @Nullable PsiType actualType,
        @Nonnull PsiElement context
    ) {
        return targetType != null && actualType != null
            && canAssign(targetType, actualType, context, ApplicableTo.METHOD_PARAMETER) == ConversionResult.OK;
    }

    @Nullable
    private static ConversionResult areTypesConvertible(
        @Nonnull PsiType targetType,
        @Nonnull PsiType actualType,
        @Nonnull PsiElement context,
        @Nonnull ApplicableTo position
    ) {
        if (context instanceof GroovyPsiElement groovyContext) {
            for (GrTypeConverter converter : GrTypeConverter.EP_NAME.getExtensions()) {
                if (!converter.isApplicableTo(position)) {
                    continue;
                }
                final ConversionResult result = converter.isConvertibleEx(targetType, actualType, groovyContext, position);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static boolean isAssignableWithoutConversions(@Nullable PsiType lType, @Nullable PsiType rType, @Nonnull PsiElement context) {
        if (lType == null || rType == null) {
            return false;
        }

        if (rType == PsiType.NULL) {
            return !(lType instanceof PsiPrimitiveType);
        }

        PsiManager manager = context.getManager();
        GlobalSearchScope scope = context.getResolveScope();

        if (rType instanceof GrTupleType rTupleType && rTupleType.getComponentTypes().length == 0) {
            if (lType instanceof PsiArrayType
                || InheritanceUtil.isInheritor(lType, JavaClassNames.JAVA_UTIL_LIST)
                || InheritanceUtil.isInheritor(lType, JavaClassNames.JAVA_UTIL_SET)) {
                return true;
            }
        }

        if (rType instanceof GrTraitType rTraitType) {
            if (isAssignableWithoutConversions(lType, rTraitType.getExprType(), context)) {
                return true;
            }
            for (PsiClassType trait : rTraitType.getTraitTypes()) {
                if (isAssignableWithoutConversions(lType, trait, context)) {
                    return true;
                }
            }
            return false;
        }

        if (isClassType(rType, GroovyCommonClassNames.GROOVY_LANG_GSTRING) && lType.equalsToText(JavaClassNames.JAVA_LANG_STRING)) {
            return true;
        }

        if (isNumericType(lType) && isNumericType(rType)) {
            lType = unboxPrimitiveTypeWrapper(lType);
            if (isClassType(lType, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL)) {
                lType = PsiType.DOUBLE;
            }
            rType = unboxPrimitiveTypeWrapper(rType);
            if (isClassType(rType, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL)) {
                rType = PsiType.DOUBLE;
            }
        }
        else {
            rType = boxPrimitiveType(rType, manager, scope);
            lType = boxPrimitiveType(lType, manager, scope);
        }

        if (rType instanceof GrMapType || rType instanceof GrTupleType) {
            Boolean result = isAssignableForNativeTypes(lType, (PsiClassType)rType, context);
            if (result != null && result) {
                return true;
            }
        }

        if (rType instanceof GrClosureType rClosureType && canMakeClosureRaw(lType)) {
            rType = rClosureType.rawType();
        }

        return TypeConversionUtil.isAssignable(lType, rType);
    }

    private static boolean canMakeClosureRaw(PsiType type) {
        if (type instanceof PsiClassType classType) {
            final PsiType[] parameters = classType.getParameters();

            return parameters.length != 1 || parameters[0] instanceof PsiWildcardType;
        }
        return true;
    }

    @Nullable
    private static Boolean isAssignableForNativeTypes(@Nonnull PsiType lType, @Nonnull PsiClassType rType, @Nonnull PsiElement context) {
        if (!(lType instanceof PsiClassType)) {
            return null;
        }
        final PsiClassType.ClassResolveResult leftResult = ((PsiClassType)lType).resolveGenerics();
        final PsiClassType.ClassResolveResult rightResult = rType.resolveGenerics();
        final PsiClass leftClass = leftResult.getElement();
        PsiClass rightClass = rightResult.getElement();
        if (rightClass == null || leftClass == null) {
            return null;
        }

        if (!InheritanceUtil.isInheritorOrSelf(rightClass, leftClass, true)) {
            return Boolean.FALSE;
        }

        PsiSubstitutor rightSubstitutor = rightResult.getSubstitutor();

        if (!leftClass.hasTypeParameters()) {
            return Boolean.TRUE;
        }
        PsiSubstitutor leftSubstitutor = leftResult.getSubstitutor();

        if (!leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
            rightSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(leftClass, rightClass, rightSubstitutor);
            rightClass = leftClass;
        }
        else if (!rightClass.hasTypeParameters()) {
            return Boolean.TRUE;
        }

        Iterator<PsiTypeParameter> li = PsiUtil.typeParametersIterator(leftClass);
        Iterator<PsiTypeParameter> ri = PsiUtil.typeParametersIterator(rightClass);
        while (li.hasNext()) {
            if (!ri.hasNext()) {
                return Boolean.FALSE;
            }
            PsiTypeParameter lp = li.next();
            PsiTypeParameter rp = ri.next();
            final PsiType typeLeft = leftSubstitutor.substitute(lp);
            if (typeLeft == null) {
                continue;
            }
            final PsiType typeRight = rightSubstitutor.substituteWithBoundsPromotion(rp);
            if (typeRight == null) {
                return Boolean.TRUE;
            }
            if (!isAssignableWithoutConversions(typeLeft, typeRight, context)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    @Nonnull
    public static ConversionResult canCast(@Nonnull PsiType targetType, @Nonnull PsiType actualType, @Nonnull PsiElement context) {
        final ConversionResult result = areTypesConvertible(targetType, actualType, context, ApplicableTo.EXPLICIT_CAST);
        if (result != null) {
            return result;
        }
        return TypeConversionUtil.areTypesConvertible(targetType, actualType) ? ConversionResult.OK : ConversionResult.ERROR;
    }

    @Nonnull
    public static ConversionResult canAssignWithinMultipleAssignment(
        @Nonnull PsiType targetType,
        @Nonnull PsiType actualType,
        @Nonnull PsiElement context
    ) {
        return isAssignableWithoutConversions(targetType, actualType, context) ? ConversionResult.OK : ConversionResult.ERROR;
    }

    public static boolean isNumericType(@Nullable PsiType type) {
        return type instanceof PsiClassType
            ? TYPE_TO_RANK.containsKey(getQualifiedName(type))
            : type instanceof PsiPrimitiveType && TypeConversionUtil.isNumericType(type);
    }

    public static PsiType unboxPrimitiveTypeWrapperAndEraseGenerics(PsiType result) {
        return TypeConversionUtil.erasure(unboxPrimitiveTypeWrapper(result));
    }

    public static PsiType unboxPrimitiveTypeWrapper(@Nullable PsiType type) {
        if (type instanceof PsiClassType classType) {
            final PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                PsiType unboxed = ourQNameToUnboxed.get(psiClass.getQualifiedName());
                if (unboxed != null) {
                    type = unboxed;
                }
            }
        }
        return type;
    }

    public static PsiType boxPrimitiveType(
        @Nullable PsiType result,
        @Nonnull PsiManager manager,
        @Nonnull GlobalSearchScope resolveScope,
        boolean boxVoid
    ) {
        if (result instanceof PsiPrimitiveType primitive && (boxVoid || result != PsiType.VOID)) {
            String boxedTypeName = primitive.getBoxedTypeName();
            if (boxedTypeName != null) {
                return GroovyPsiManager.getInstance(manager.getProject()).createTypeByFQClassName(boxedTypeName, resolveScope);
            }
        }

        return result;
    }

    public static PsiType boxPrimitiveType(@Nullable PsiType result, @Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope) {
        return boxPrimitiveType(result, manager, resolveScope, false);
    }

    @Nonnull
    public static PsiClassType createType(String fqName, @Nonnull PsiElement context) {
        return createTypeByFQClassName(fqName, context);
    }

    @Nonnull
    public static PsiClassType getJavaLangObject(@Nonnull PsiElement context) {
        return LazyFqnClassType.getLazyType(JavaClassNames.JAVA_LANG_OBJECT, context);
    }

    @Nullable
    public static PsiType getLeastUpperBoundNullable(@Nullable PsiType type1, @Nullable PsiType type2, @Nonnull PsiManager manager) {
        if (type1 == null) {
            return type2;
        }
        if (type2 == null) {
            return type1;
        }
        return getLeastUpperBound(type1, type2, manager);
    }

    @Nullable
    public static PsiType getLeastUpperBoundNullable(@Nonnull Iterable<PsiType> collection, @Nonnull PsiManager manager) {
        Iterator<PsiType> iterator = collection.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        PsiType result = iterator.next();
        while (iterator.hasNext()) {
            result = getLeastUpperBoundNullable(result, iterator.next(), manager);
        }
        return result;
    }

    @Nullable
    public static PsiType getLeastUpperBound(@Nonnull PsiType type1, @Nonnull PsiType type2, PsiManager manager) {
        if (type1 instanceof GrTupleType tuple1 && type2 instanceof GrTupleType tuple2) {
            PsiType[] components1 = tuple1.getComponentTypes();
            PsiType[] components2 = tuple2.getComponentTypes();

            if (components1.length == 0) {
                return genNewListBy(type2, manager);
            }
            if (components2.length == 0) {
                return genNewListBy(type1, manager);
            }

            PsiType[] components3 = PsiType.createArray(Math.min(components1.length, components2.length));
            for (int i = 0; i < components3.length; i++) {
                PsiType c1 = components1[i];
                PsiType c2 = components2[i];
                if (c1 == null || c2 == null) {
                    components3[i] = null;
                }
                else {
                    components3[i] = getLeastUpperBound(c1, c2, manager);
                }
            }
            return new GrImmediateTupleType(
                components3,
                JavaPsiFacade.getInstance(manager.getProject()),
                tuple1.getScope().intersectWith(tuple2.getResolveScope())
            );
        }
        else if (checkEmptyListAndList(type1, type2)) {
            return genNewListBy(type2, manager);
        }
        else if (checkEmptyListAndList(type2, type1)) {
            return genNewListBy(type1, manager);
        }
        else if (type1 instanceof GrMapType mapType1 && type2 instanceof GrMapType mapType2) {
            return GrMapType.merge(mapType1, mapType2);
        }
        else if (checkEmptyMapAndMap(type1, type2)) {
            return genNewMapBy(type2, manager);
        }
        else if (checkEmptyMapAndMap(type2, type1)) {
            return genNewMapBy(type1, manager);
        }
        else if (type1 instanceof GrClosureType clType1 && type2 instanceof GrClosureType clType2) {
            GrSignature signature1 = clType1.getSignature();
            GrSignature signature2 = clType2.getSignature();

            if (signature1 instanceof GrClosureSignature clSig1 && signature2 instanceof GrClosureSignature clSig2
                && clSig1.getParameterCount() == clSig2.getParameterCount()) {
                final GrClosureSignature signature = GrImmediateClosureSignatureImpl.getLeastUpperBound(clSig1, clSig2, manager);
                if (signature != null) {
                    GlobalSearchScope scope = clType1.getResolveScope().intersectWith(clType2.getResolveScope());
                    final LanguageLevel languageLevel = ComparatorUtil.max(clType1.getLanguageLevel(), clType2.getLanguageLevel());
                    return GrClosureType.create(signature, scope, JavaPsiFacade.getInstance(manager.getProject()), languageLevel, true);
                }
            }
        }
        else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(getQualifiedName(type1))
            && JavaClassNames.JAVA_LANG_STRING.equals(getQualifiedName(type2))) {
            return type2;
        }
        else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(getQualifiedName(type2))
            && JavaClassNames.JAVA_LANG_STRING.equals(getQualifiedName(type1))) {
            return type1;
        }
        return GenericsUtil.getLeastUpperBound(type1, type2, manager);
    }

    private static boolean checkEmptyListAndList(PsiType type1, PsiType type2) {
        if (type1 instanceof GrTupleType tupleType1) {
            PsiType[] types = tupleType1.getComponentTypes();
            if (types.length == 0 && InheritanceUtil.isInheritor(type2, JavaClassNames.JAVA_UTIL_LIST)) {
                return true;
            }
        }

        return false;
    }

    private static PsiType genNewListBy(PsiType genericOwner, PsiManager manager) {
        PsiClass list =
            JavaPsiFacade.getInstance(manager.getProject()).findClass(JavaClassNames.JAVA_UTIL_LIST, genericOwner.getResolveScope());
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
        if (list == null) {
            return factory.createTypeFromText(JavaClassNames.JAVA_UTIL_LIST, null);
        }
        return factory.createType(list, PsiUtil.extractIterableTypeParameter(genericOwner, false));
    }

    private static boolean checkEmptyMapAndMap(PsiType type1, PsiType type2) {
        return type1 instanceof GrMapType mapType1 && mapType1.isEmpty()
            && InheritanceUtil.isInheritor(type2, JavaClassNames.JAVA_UTIL_MAP);
    }

    private static PsiType genNewMapBy(PsiType genericOwner, PsiManager manager) {
        PsiClass map =
            JavaPsiFacade.getInstance(manager.getProject()).findClass(JavaClassNames.JAVA_UTIL_MAP, genericOwner.getResolveScope());
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
        if (map == null) {
            return factory.createTypeFromText(JavaClassNames.JAVA_UTIL_MAP, null);
        }

        final PsiType key = PsiUtil.substituteTypeParameter(genericOwner, JavaClassNames.JAVA_UTIL_MAP, 0, false);
        final PsiType value = PsiUtil.substituteTypeParameter(genericOwner, JavaClassNames.JAVA_UTIL_MAP, 1, false);
        return factory.createType(map, key, value);
    }

    @Nullable
    public static PsiType getPsiType(PsiElement context, IElementType elemType) {
        if (elemType == GroovyTokenTypes.kNULL) {
            return PsiType.NULL;
        }
        final String typeName = getBoxedTypeName(elemType);
        if (typeName != null) {
            return createTypeByFQClassName(typeName, context);
        }
        return null;
    }

    @Nullable
    public static String getBoxedTypeName(IElementType elemType) {
        return ourPrimitiveTypesToClassNames.get(elemType);
    }

    @Nonnull
    public static PsiType getLeastUpperBound(PsiClass[] classes, PsiManager manager) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

        if (classes.length == 0) {
            return factory.createTypeByFQClassName(JavaClassNames.JAVA_LANG_OBJECT);
        }

        PsiType type = factory.createType(classes[0]);

        for (int i = 1; i < classes.length; i++) {
            PsiType t = getLeastUpperBound(type, factory.createType(classes[i]), manager);
            if (t != null) {
                type = t;
            }
        }

        return type;
    }

    public static boolean isClassType(@Nullable PsiType type, @Nonnull String qName) {
        return qName.equals(getQualifiedName(type));
    }

    public static PsiSubstitutor composeSubstitutors(PsiSubstitutor s1, PsiSubstitutor s2) {
        final Map<PsiTypeParameter, PsiType> map = s1.getSubstitutionMap();
        Map<PsiTypeParameter, PsiType> result = new HashMap<>(map.size());
        for (PsiTypeParameter parameter : map.keySet()) {
            result.put(parameter, s2.substitute(map.get(parameter)));
        }
        final Map<PsiTypeParameter, PsiType> map2 = s2.getSubstitutionMap();
        for (PsiTypeParameter parameter : map2.keySet()) {
            if (!result.containsKey(parameter)) {
                result.put(parameter, map2.get(parameter));
            }
        }
        return PsiSubstitutorImpl.createSubstitutor(result);
    }

    @Nonnull
    public static PsiClassType createTypeByFQClassName(@Nonnull String fqName, @Nonnull PsiElement context) {
        return GroovyPsiManager.getInstance(context.getProject()).createTypeByFQClassName(fqName, context.getResolveScope());
    }

    @Nullable
    public static PsiType createJavaLangClassType(@Nullable PsiType type, Project project, GlobalSearchScope resolveScope) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiType result = null;
        PsiClass javaLangClass = facade.findClass(JavaClassNames.JAVA_LANG_CLASS, resolveScope);
        if (javaLangClass != null) {
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            final PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
            if (typeParameters.length == 1) {
                substitutor = substitutor.put(typeParameters[0], type);
            }
            result = facade.getElementFactory().createType(javaLangClass, substitutor);
        }
        return result;
    }

    @Nonnull
    public static PsiPrimitiveType getPrimitiveTypeByText(String typeText) {
        for (final PsiPrimitiveType primitive : PRIMITIVES) {
            if (PsiType.VOID.equals(primitive)) {
                return primitive;
            }
            if (primitive.getCanonicalText().equals(typeText)) {
                return primitive;
            }
        }

        assert false : "Unknown primitive type";
        return null;
    }

    @Nonnull
    public static PsiClassType createListType(@Nonnull PsiClass elements) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(elements.getProject());
        GlobalSearchScope resolveScope = elements.getResolveScope();
        PsiClass listClass = facade.findClass(JavaClassNames.JAVA_UTIL_LIST, resolveScope);
        if (listClass == null) {
            return facade.getElementFactory().createTypeByFQClassName(JavaClassNames.JAVA_UTIL_LIST, resolveScope);
        }
        return facade.getElementFactory().createType(listClass, facade.getElementFactory().createType(elements));
    }

    @Nonnull
    public static PsiType createSetType(@Nonnull PsiElement context, @Nonnull PsiType type) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
        GlobalSearchScope resolveScope = context.getResolveScope();

        PsiClass setClass = facade.findClass(JavaClassNames.JAVA_UTIL_SET, resolveScope);
        if (setClass != null && setClass.getTypeParameters().length == 1) {
            return facade.getElementFactory().createType(setClass, type);
        }

        return facade.getElementFactory().createTypeByFQClassName(JavaClassNames.JAVA_UTIL_SET, resolveScope);
    }

    public static boolean isAnnotatedCheckHierarchyWithCache(@Nonnull PsiClass aClass, @Nonnull String annotationFQN) {
        Map<String, PsiClass> classMap = ClassUtil.getSuperClassesWithCache(aClass);

        for (PsiClass psiClass : classMap.values()) {
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList != null && modifierList.findAnnotation(annotationFQN) != null) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    public static PsiType substituteAndNormalizeType(
        @Nullable PsiType type,
        @Nonnull PsiSubstitutor substitutor,
        @Nullable SpreadState state,
        @Nonnull GrExpression expression
    ) {
        if (type == null) {
            return null;
        }
        type = substitutor.substitute(type);
        if (type == null) {
            return null;
        }
        type = PsiImplUtil.normalizeWildcardTypeByPosition(type, expression);
        type = SpreadState.apply(type, state, expression.getProject());
        return type;
    }

    @Nullable
    public static PsiType getItemType(@Nullable PsiType containerType) {
        if (containerType == null) {
            return null;
        }

        if (containerType instanceof PsiArrayType arrayType) {
            return arrayType.getComponentType();
        }
        return PsiUtil.extractIterableTypeParameter(containerType, false);
    }

    @Nullable
    public static PsiType inferAnnotationMemberValueType(final GrAnnotationMemberValue value) {
        if (value instanceof GrExpression expression) {
            return expression.getType();
        }

        else if (value instanceof GrAnnotation annotation) {
            return annotation.getClassReference().resolve() instanceof PsiClass annotationClass
                ? JavaPsiFacade.getElementFactory(value.getProject()).createType(annotationClass, PsiSubstitutor.EMPTY)
                : null;
        }
        else if (value instanceof GrAnnotationArrayInitializer annotationArrayInitializer) {
            return getTupleByAnnotationArrayInitializer(annotationArrayInitializer);
        }

        return null;
    }

    public static PsiType getTupleByAnnotationArrayInitializer(final GrAnnotationArrayInitializer value) {
        return new GrTupleType(value.getResolveScope(), JavaPsiFacade.getInstance(value.getProject())) {
            @Nonnull
            @Override
            protected PsiType[] inferComponents() {
                final GrAnnotationMemberValue[] initializers = value.getInitializers();
                return ContainerUtil.map(initializers, TypesUtil::inferAnnotationMemberValueType, PsiType.createArray(initializers.length));
            }

            @Override
            public boolean isValid() {
                return value.isValid();
            }
        };
    }

    public static boolean resolvesTo(PsiType type, String fqn) {
        if (type instanceof PsiClassType classType) {
            final PsiClass resolved = classType.resolve();
            return resolved != null && fqn.equals(resolved.getQualifiedName());
        }
        return false;
    }

    @Nullable
    public static PsiType rawSecondGeneric(PsiType type, Project project) {
        if (!(type instanceof PsiClassType)) {
            return null;
        }

        final PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
        final PsiClass element = result.getElement();
        if (element == null) {
            return null;
        }

        final PsiType[] parameters = ((PsiClassType)type).getParameters();

        boolean changed = false;
        for (int i = 0; i < parameters.length; i++) {
            PsiType parameter = parameters[i];
            if (parameter == null) {
                continue;
            }

            final Ref<PsiType> newParam = new Ref<>();
            parameter.accept(new PsiTypeVisitorEx<>() {
                @Nullable
                @Override
                public Object visitClassType(PsiClassType classType) {
                    if (classType.getParameterCount() > 0) {
                        newParam.set(classType.rawType());
                    }
                    return null;
                }

                @Nullable
                @Override
                public Object visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
                    newParam.set(capturedWildcardType.getWildcard().getBound());
                    return null;
                }

                @Nullable
                @Override
                public Object visitWildcardType(PsiWildcardType wildcardType) {
                    newParam.set(wildcardType.getBound());
                    return null;
                }
            });

            if (!newParam.isNull()) {
                changed = true;
                parameters[i] = newParam.get();
            }
        }
        if (!changed) {
            return null;
        }
        return JavaPsiFacade.getElementFactory(project).createType(element, parameters);
    }

    public static boolean isPsiClassTypeToClosure(PsiType type) {
        if (type instanceof PsiClassType classType) {
            final PsiClass psiClass = classType.resolve();
            return psiClass != null && GroovyCommonClassNames.GROOVY_LANG_CLOSURE.equals(psiClass.getQualifiedName());
        }
        return false;
    }

    @Nullable
    public static String getQualifiedName(@Nullable PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved instanceof PsiAnonymousClass anonymousClass) {
                return getQualifiedName(anonymousClass.getBaseClassType());
            }
            else if (resolved != null) {
                return resolved.getQualifiedName();
            }
            else {
                return PsiNameHelper.getQualifiedClassName(type.getCanonicalText(), true);
            }
        }

        return null;
    }

    public static boolean isEnum(PsiType type) {
        if (type instanceof PsiClassType classType) {
            final PsiClass resolved = classType.resolve();
            return resolved != null && resolved.isEnum();
        }
        return false;
    }
}
