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
package org.jetbrains.plugins.groovy.impl.annotator;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.ClassUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.java.impl.refactoring.introduceParameter.ExpressionConverter;
import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.annotation.Annotation;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.impl.psi.LightElement;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.impl.annotator.checkers.AnnotationChecker;
import org.jetbrains.plugins.groovy.impl.annotator.checkers.CustomAnnotationChecker;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.*;
import org.jetbrains.plugins.groovy.impl.codeInspection.bugs.GrModifierFix;
import org.jetbrains.plugins.groovy.impl.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.impl.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.impl.lang.psi.api.statements.expressions.GrUnAmbiguousClosureContainer;
import org.jetbrains.plugins.groovy.impl.lang.resolve.ast.GrInheritConstructorContributor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author ven
 */
@SuppressWarnings({"unchecked"})
public class GroovyAnnotator extends GroovyElementVisitor {
    private static final Logger LOG = Logger.getInstance(GroovyAnnotator.class);
    public static final Predicate<PsiClass> IS_INTERFACE = PsiClass::isInterface;
    private static final Predicate<PsiClass> IS_NOT_INTERFACE = aClass -> !aClass.isInterface();
    public static final Predicate<PsiClass> IS_TRAIT = GrTraitUtil::isTrait;

    private final AnnotationHolder myHolder;

    public GroovyAnnotator(@Nonnull AnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitTypeArgumentList(GrTypeArgumentList typeArgumentList) {
        PsiElement parent = typeArgumentList.getParent();
        if (!(parent instanceof GrReferenceElement refElem)) {
            return;
        }

        GroovyResolveResult resolveResult = refElem.advancedResolve();
        PsiElement resolved = resolveResult.getElement();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();

        if (resolved == null) {
            return;
        }

        if (!(resolved instanceof PsiTypeParameterListOwner typeParamListOwner)) {
            myHolder.createWarningAnnotation(typeArgumentList, GroovyLocalize.typeArgumentListIsNotAllowedHere().get());
            return;
        }

        if (refElem instanceof GrCodeReferenceElement codeRefElem && !checkDiamonds(codeRefElem, myHolder)) {
            return;
        }

        PsiTypeParameter[] parameters = typeParamListOwner.getTypeParameters();
        GrTypeElement[] arguments = typeArgumentList.getTypeArgumentElements();

        if (arguments.length != parameters.length) {
            myHolder.createWarningAnnotation(
                typeArgumentList,
                GroovyLocalize.wrongNumberOfTypeArguments(arguments.length, parameters.length).get()
            );
            return;
        }

        for (int i = 0; i < parameters.length; i++) {
            PsiTypeParameter parameter = parameters[i];
            PsiClassType[] superTypes = parameter.getExtendsListTypes();
            PsiType argType = arguments[i].getType();
            for (PsiClassType superType : superTypes) {
                PsiType substitutedSuper = substitutor.substitute(superType);
                if (substitutedSuper != null && !substitutedSuper.isAssignableFrom(argType)) {
                    myHolder.createWarningAnnotation(
                        arguments[i],
                        GroovyLocalize.typeArgument0IsNotInItsBoundShouldExtend1(
                            argType.getCanonicalText(),
                            superType.getCanonicalText()
                        ).get()
                    );
                    break;
                }
            }
        }
    }

    @Override
    public void visitNamedArgument(GrNamedArgument argument) {
        if (argument.getParent() instanceof GrArgumentList argList && argList.getParent() instanceof GrIndexProperty) {
            myHolder.createErrorAnnotation(argument, GroovyLocalize.namedArgumentsAreNotAllowedInsideIndexOperations().get());
        }
    }

    @Override
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        super.visitApplicationStatement(applicationStatement);
        checkForCommandExpressionSyntax(applicationStatement);
    }

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        super.visitMethodCallExpression(methodCallExpression);
        checkForCommandExpressionSyntax(methodCallExpression);
    }

    private void checkForCommandExpressionSyntax(GrMethodCall methodCall) {
        GroovyConfigUtils groovyConfig = GroovyConfigUtils.getInstance();
        if (methodCall.isCommandExpression() && !groovyConfig.isVersionAtLeast(methodCall, GroovyConfigUtils.GROOVY1_8)) {
            myHolder.createErrorAnnotation(
                methodCall,
                GroovyLocalize.isNotSupportedInVersion(groovyConfig.getSDKVersion(methodCall)).get()
            );
        }
    }

    @Override
    public void visitElement(GroovyPsiElement element) {
        if (element.getParent() instanceof GrDocReferenceElement) {
            checkGrDocReferenceElement(myHolder, element);
        }
    }

    @Override
    public void visitTryStatement(GrTryCatchStatement statement) {
        GrCatchClause[] clauses = statement.getCatchClauses();
        List<PsiType> usedExceptions = new ArrayList<>();

        for (GrCatchClause clause : clauses) {
            GrParameter parameter = clause.getParameter();
            if (parameter == null) {
                continue;
            }

            GrTypeElement typeElement = parameter.getTypeElementGroovy();
            PsiType type = typeElement != null
                ? typeElement.getType()
                : TypesUtil.createType(CommonClassNames.JAVA_LANG_EXCEPTION, statement);

            if (typeElement instanceof GrDisjunctionTypeElement disjunctionTypeElem) {
                GrTypeElement[] elements = disjunctionTypeElem.getTypeElements();
                PsiType[] types = PsiType.createArray(elements.length);
                for (int i = 0; i < elements.length; i++) {
                    types[i] = elements[i].getType();
                }

                List<PsiType> usedInsideDisjunction = new ArrayList<>();
                for (int i = 0; i < types.length; i++) {
                    if (checkExceptionUsed(usedExceptions, parameter, elements[i], types[i])) {
                        usedInsideDisjunction.add(types[i]);
                        for (int j = 0; j < types.length; j++) {
                            if (i != j && types[j].isAssignableFrom(types[i])) {
                                myHolder.createWarningAnnotation(
                                        elements[i],
                                        GroovyLocalize.unnecessaryType(types[i].getCanonicalText(), types[j].getCanonicalText()).get()
                                    )
                                    .registerFix(new GrRemoveExceptionFix(true));
                            }
                        }
                    }
                }

                usedExceptions.addAll(usedInsideDisjunction);
            }
            else if (checkExceptionUsed(usedExceptions, parameter, typeElement, type)) {
                usedExceptions.add(type);
            }
        }
    }

    @Override
    public void visitCatchClause(GrCatchClause clause) {
        GrParameter parameter = clause.getParameter();
        if (parameter == null) {
            return;
        }

        GrTypeElement typeElement = parameter.getTypeElementGroovy();
        if (typeElement != null) {
            PsiType type = typeElement.getType();
            if (type instanceof PsiClassType classType && classType.resolve() == null) {
                return; //don't highlight unresolved types
            }
            PsiClassType throwable = TypesUtil.createType(CommonClassNames.JAVA_LANG_THROWABLE, clause);
            if (!throwable.isAssignableFrom(type)) {
                myHolder.createErrorAnnotation(
                    typeElement,
                    GroovyLocalize.catchStatementParameterTypeShouldBeASubclassOfThrowable().get()
                );
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitDocComment(GrDocComment comment) {
        String text = comment.getText();
        if (!text.endsWith("*/")) {
            TextRange range = comment.getTextRange();
            myHolder.createErrorAnnotation(
                new TextRange(range.getEndOffset() - 1, range.getEndOffset()),
                GroovyLocalize.docEndExpected().get()
            );
        }
    }

    @Override
    public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
        if (variableDeclaration.isTuple()) {
            GrModifierList list = variableDeclaration.getModifierList();

            PsiElement last = PsiUtil.skipWhitespacesAndComments(list.getLastChild(), false);
            if (last != null) {
                IElementType type = last.getNode().getElementType();
                if (type != GroovyTokenTypes.kDEF) {
                    myHolder.createErrorAnnotation(
                        list,
                        GroovyLocalize.tupleDeclarationShouldEndWithDefModifier().get()
                    );
                }
            }
            else {
                myHolder.createErrorAnnotation(
                    list,
                    GroovyLocalize.tupleDeclarationShouldEndWithDefModifier().get()
                );
            }
        }
    }

    private boolean checkExceptionUsed(
        List<PsiType> usedExceptions,
        GrParameter parameter,
        GrTypeElement typeElement,
        PsiType type
    ) {
        for (PsiType exception : usedExceptions) {
            if (exception.isAssignableFrom(type)) {
                myHolder.createWarningAnnotation(
                        typeElement != null
                            ? typeElement
                            : parameter.getNameIdentifierGroovy(),
                        GroovyLocalize.exception0HasAlreadyBeenCaught(type.getCanonicalText()).get()
                    )
                    .registerFix(new GrRemoveExceptionFix(parameter.getTypeElementGroovy() instanceof GrDisjunctionTypeElement));
                return false;
            }
        }
        return true;
    }

    @Override
    @RequiredReadAction
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        checkStringNameIdentifier(referenceExpression);
        checkThisOrSuperReferenceExpression(referenceExpression, myHolder);
        checkFinalFieldAccess(referenceExpression);
        checkFinalParameterAccess(referenceExpression);

        if (ResolveUtil.isKeyOfMap(referenceExpression)) {
            PsiElement nameElement = referenceExpression.getReferenceNameElement();
            LOG.assertTrue(nameElement != null);
            myHolder.createInfoAnnotation(nameElement, null).setTextAttributes(GroovySyntaxHighlighter.MAP_KEY);
        }
        else if (ResolveUtil.isClassReference(referenceExpression)) {
            PsiElement nameElement = referenceExpression.getReferenceNameElement();
            LOG.assertTrue(nameElement != null);
            myHolder.createInfoAnnotation(nameElement, null).setTextAttributes(GroovySyntaxHighlighter.KEYWORD);
        }
    }

    private void checkFinalParameterAccess(GrReferenceExpression ref) {
        PsiElement resolved = ref.resolve();

        if (resolved instanceof GrParameter parameter
            && parameter.isPhysical()
            && parameter.hasModifierProperty(PsiModifier.FINAL)
            && PsiUtil.isLValue(ref)) {
            if (parameter.getDeclarationScope() instanceof PsiMethod) {
                myHolder.createErrorAnnotation(
                    ref,
                    GroovyLocalize.cannotAssignAValueToFinalParameter0(parameter.getName()).get()
                );
            }
        }
    }

    private void checkFinalFieldAccess(@Nonnull GrReferenceExpression ref) {
        PsiElement resolved = ref.resolve();

        if (resolved instanceof GrField field && resolved.isPhysical() && field.isFinal() && PsiUtil.isLValue(ref)) {
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && PsiTreeUtil.isAncestor(containingClass, ref, true)) {
                GrMember container = GrHighlightUtil.findClassMemberContainer(ref, containingClass);

                if (field.isStatic()) {
                    if (container instanceof GrClassInitializer classInitializer && classInitializer.isStatic()) {
                        return;
                    }
                }
                else if (container instanceof GrMethod method && method.isConstructor()
                    || container instanceof GrClassInitializer classInitializer && !classInitializer.isStatic()) {
                    return;
                }

                myHolder.createErrorAnnotation(
                    ref,
                    GroovyLocalize.cannotAssignAValueToFinalField0(field.getName()).get()
                );
            }
        }
    }

    @RequiredReadAction
    private void checkStringNameIdentifier(GrReferenceExpression ref) {
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement == null) {
            return;
        }

        IElementType elementType = nameElement.getNode().getElementType();
        if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL) {
            checkStringLiteral(nameElement);
        }
        else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
            checkRegexLiteral(nameElement);
        }
    }

    @Override
    @RequiredReadAction
    public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
        if (typeDefinitionBody.getParent() instanceof GrAnonymousClassDefinition) {
            PsiElement prev = typeDefinitionBody.getPrevSibling();
            if (PsiUtil.isLineFeed(prev)) {
                myHolder.createErrorAnnotation(typeDefinitionBody, GroovyLocalize.ambiguousCodeBlock().get());
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
        PsiElement parent = typeDefinition.getParent();
        if (!(typeDefinition.isAnonymous()
            || parent instanceof GrTypeDefinitionBody
            || parent instanceof GroovyFile
            || typeDefinition instanceof GrTypeParameter)) {
            TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
            Annotation errorAnnotation = myHolder.createErrorAnnotation(
                range,
                GroovyLocalize.classDefinitionIsNotExpectedHere().get()
            );
            errorAnnotation.registerFix(new GrMoveClassToCorrectPlaceFix(typeDefinition));
        }
        checkTypeDefinition(myHolder, typeDefinition);

        checkDuplicateMethod(typeDefinition, myHolder);
        checkImplementedMethodsOfClass(myHolder, typeDefinition);
        checkConstructors(myHolder, typeDefinition);

        checkAnnotationCollector(myHolder, typeDefinition);

        checkSameNameMethodsWithDifferentAccessModifiers(myHolder, typeDefinition.getCodeMethods());
    }

    private static void checkSameNameMethodsWithDifferentAccessModifiers(AnnotationHolder holder, GrMethod[] methods) {
        MultiMap<String, GrMethod> map = MultiMap.create();
        for (GrMethod method : methods) {
            if (!method.isConstructor()) {
                map.putValue(method.getName(), method);
            }
        }

        for (Map.Entry<String, Collection<GrMethod>> entry : map.entrySet()) {
            Collection<GrMethod> collection = entry.getValue();
            if (collection.size() > 1 && !sameAccessModifier(collection)) {
                for (GrMethod method : collection) {
                    holder.createErrorAnnotation(
                        GrHighlightUtil.getMethodHeaderTextRange(method),
                        GroovyLocalize.mixingPrivateAndPublicProtectedMethodsOfTheSameName().get()
                    );
                }
            }
        }
    }

    private static boolean sameAccessModifier(Collection<GrMethod> collection) {
        Iterator<GrMethod> iterator = collection.iterator();
        GrMethod method = iterator.next();
        boolean privateAccess = PsiModifier.PRIVATE.equals(VisibilityUtil.getVisibilityModifier(method.getModifierList()));

        while (iterator.hasNext()) {
            GrMethod next = iterator.next();
            if (privateAccess != PsiModifier.PRIVATE.equals(VisibilityUtil.getVisibilityModifier(next.getModifierList()))) {
                return false;
            }
        }

        return true;
    }

    private static void checkAnnotationCollector(AnnotationHolder holder, GrTypeDefinition definition) {
        if (definition.isAnnotationType()
            && GrAnnotationCollector.findAnnotationCollector(definition) != null
            && definition.getCodeMethods().length > 0) {
            holder.createErrorAnnotation(
                definition.getNameIdentifierGroovy(),
                GroovyLocalize.annotationCollectorCannotHaveAttributes().get()
            );
        }
    }

    @RequiredReadAction
    private static void checkConstructors(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
        if (typeDefinition.isEnum() || typeDefinition.isInterface() || typeDefinition.isAnonymous()
            || typeDefinition instanceof GrTypeParameter) {
            return;
        }
        PsiClass superClass = typeDefinition.getSuperClass();
        if (superClass == null) {
            return;
        }

        if (GrInheritConstructorContributor.hasInheritConstructorsAnnotation(typeDefinition)) {
            return;
        }

        PsiMethod defConstructor = getDefaultConstructor(superClass);
        boolean hasImplicitDefConstructor = superClass.getConstructors().length == 0;

        PsiMethod[] constructors = typeDefinition.getCodeConstructors();
        String qName = superClass.getQualifiedName();
        if (constructors.length == 0) {
            if (!hasImplicitDefConstructor && (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor))) {
                TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
                holder.createErrorAnnotation(
                        range,
                        GroovyLocalize.thereIsNoDefaultConstructorAvailableInClass0(qName).get()
                    )
                    .registerFix(QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(typeDefinition));
            }
            return;
        }
        for (PsiMethod method : constructors) {
            if (method instanceof GrMethod grMethod) {
                GrOpenBlock block = grMethod.getBlock();
                if (block == null) {
                    continue;
                }
                GrStatement[] statements = block.getStatements();
                if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
                    continue;
                }

                if (!hasImplicitDefConstructor && (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor))) {
                    holder.createErrorAnnotation(
                        GrHighlightUtil.getMethodHeaderTextRange(method),
                        GroovyLocalize.thereIsNoDefaultConstructorAvailableInClass0(qName).get()
                    );
                }
            }
        }

        checkRecursiveConstructors(holder, constructors);
    }

    @Override
    public void visitEnumConstant(GrEnumConstant enumConstant) {
        super.visitEnumConstant(enumConstant);
        GrArgumentList argumentList = enumConstant.getArgumentList();

        if (argumentList != null && PsiImplUtil.hasNamedArguments(argumentList) && !PsiImplUtil.hasExpressionArguments(argumentList)) {
            PsiMethod constructor = enumConstant.resolveConstructor();
            if (constructor != null) {
                if (!PsiUtil.isConstructorHasRequiredParameters(constructor)) {
                    myHolder.createErrorAnnotation(
                        argumentList,
                        GroovyLocalize.theUsageOfAMapEntryExpressionToInitializeAnEnumIsCurrentlyNotSupported().get()
                    );
                }
            }
        }
    }

    private static void checkRecursiveConstructors(AnnotationHolder holder, PsiMethod[] constructors) {
        Map<PsiMethod, PsiMethod> nodes = new HashMap<>(constructors.length);

        Set<PsiMethod> set = Set.of(constructors);

        for (PsiMethod constructor : constructors) {
            if (!(constructor instanceof GrMethod method)) {
                continue;
            }

            GrOpenBlock block = method.getBlock();
            if (block == null) {
                continue;
            }

            GrStatement[] statements = block.getStatements();
            if (statements.length <= 0 || !(statements[0] instanceof GrConstructorInvocation constructorInvocation)) {
                continue;
            }

            PsiMethod resolved = constructorInvocation.resolveMethod();
            if (!set.contains(resolved)) {
                continue;
            }

            nodes.put(constructor, resolved);
        }

        Set<PsiMethod> checked = new HashSet<>();

        Set<PsiMethod> current;
        for (PsiMethod constructor : constructors) {
            if (!checked.add(constructor)) {
                continue;
            }

            current = new HashSet<>();
            current.add(constructor);
            for (constructor = nodes.get(constructor);
                 constructor != null && current.add(constructor);
                 constructor = nodes.get(constructor)) {
                checked.add(constructor);
            }

            if (constructor != null) {
                PsiMethod circleStart = constructor;
                do {
                    holder.createErrorAnnotation(
                        GrHighlightUtil.getMethodHeaderTextRange(constructor),
                        GroovyLocalize.recursiveConstructorInvocation().get()
                    );
                    constructor = nodes.get(constructor);
                }
                while (constructor != circleStart);
            }
        }
    }

    @Override
    public void visitUnaryExpression(GrUnaryExpression expression) {
        if (expression.getOperationTokenType() == GroovyTokenTypes.mINC || expression.getOperationTokenType() == GroovyTokenTypes.mDEC) {
            if (expression.getOperand() instanceof GrReferenceExpression refExpr && refExpr.getQualifier() == null) {
                GrTraitTypeDefinition trait = PsiTreeUtil.getParentOfType(refExpr, GrTraitTypeDefinition.class);
                if (trait != null) {
                    PsiElement resolved = refExpr.resolve();
                    if (resolved instanceof GrField field && field.getContainingClass() instanceof GrTraitTypeDefinition) {
                        myHolder.createErrorAnnotation(
                            expression,
                            GroovyBundle.message(
                                "0.expressions.on.trait.fields.properties.are.not.supported.in.traits",
                                expression.getOperationToken().getText()
                            )
                        );
                    }
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitOpenBlock(GrOpenBlock block) {
        if (block.getParent() instanceof GrMethod method) {
            if (method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)
                || GrTraitUtil.isInterface(method.getContainingClass())) {
                Annotation annotation = myHolder.createErrorAnnotation(
                    block,
                    GroovyLocalize.abstractMethodsMustNotHaveBody().get()
                );
                registerMakeAbstractMethodNotAbstractFix(annotation, method, true);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitMethod(GrMethod method) {
        checkMethodWithTypeParamsShouldHaveReturnType(myHolder, method);
        checkInnerMethod(myHolder, method);
        checkOptionalParametersInAbstractMethod(myHolder, method);

        checkConstructorOfImmutableClass(myHolder, method);
        checkGetterOfImmutable(myHolder, method);

        PsiElement nameIdentifier = method.getNameIdentifierGroovy();
        if (nameIdentifier.getNode().getElementType() == GroovyTokenTypes.mSTRING_LITERAL) {
            checkStringLiteral(nameIdentifier);
        }

        GrOpenBlock block = method.getBlock();
        if (block != null && TypeInferenceHelper.isTooComplexTooAnalyze(block)) {
            myHolder.createWeakWarningAnnotation(
                nameIdentifier,
                GroovyLocalize.method0IsTooComplexTooAnalyze(method.getName()).get()
            );
        }

        PsiClass containingClass = method.getContainingClass();
        if (method.isConstructor()) {
            if (containingClass instanceof GrAnonymousClassDefinition) {
                myHolder.createErrorAnnotation(nameIdentifier, GroovyLocalize.constructorsAreNotAllowedInAnonymousClass().get());
            }
            else if (containingClass != null && containingClass.isInterface()) {
                myHolder.createErrorAnnotation(nameIdentifier, GroovyLocalize.constructorsAreNotAllowedInInterface().get());
            }
        }

        if (method.getBlock() == null && !method.hasModifierProperty(PsiModifier.NATIVE) && !GrTraitUtil.isMethodAbstract(method)) {
            Annotation annotation = myHolder.createErrorAnnotation(
                nameIdentifier,
                GroovyLocalize.notAbstractMethodShouldHaveBody().get()
            );
            //annotation.registerFix(new AddMethodBodyFix(method)); //todo make intentions work
            //registerFix(annotation, new GrModifierFix(method, ABSTRACT, false, true,
            // GrModifierFix.MODIFIER_LIST_OWNER), method);
        }

        checkOverridingMethod(myHolder, method);
    }

    private static void checkGetterOfImmutable(AnnotationHolder holder, GrMethod method) {
        if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) {
            return;
        }

        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return;
        }

        PsiModifierList aClassModifierList = aClass.getModifierList();
        if (aClassModifierList == null) {
            return;
        }

        if (!PsiImplUtil.hasImmutableAnnotation(aClassModifierList)) {
            return;
        }

        PsiField field = GroovyPropertyUtils.findFieldForAccessor(method, false);
        if (field == null || !(field instanceof GrField grField)) {
            return;
        }

        GrModifierList fieldModifierList = grField.getModifierList();
        if (fieldModifierList == null) {
            return;
        }

        if (fieldModifierList.hasExplicitVisibilityModifiers()) {
            return;
        }

        holder.createErrorAnnotation(
            method.getNameIdentifierGroovy(),
            GroovyBundle.message("repetitive.method.name.0", method.getName())
        );
    }

    private static void checkConstructorOfImmutableClass(AnnotationHolder holder, GrMethod method) {
        if (!method.isConstructor()) {
            return;
        }

        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return;
        }

        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
            return;
        }

        if (!PsiImplUtil.hasImmutableAnnotation(modifierList)) {
            return;
        }

        holder.createErrorAnnotation(
            method.getNameIdentifierGroovy(),
            GroovyBundle.message("explicit.constructors.are.not.allowed.in.immutable.class")
        );
    }

    private static void checkOverridingMethod(@Nonnull AnnotationHolder holder, @Nonnull GrMethod method) {
        List<HierarchicalMethodSignature> signatures = method.getHierarchicalMethodSignature().getSuperSignatures();

        for (HierarchicalMethodSignature signature : signatures) {
            PsiMethod superMethod = signature.getMethod();
            if (superMethod.isFinal()) {
                String current = GroovyPresentationUtil.getSignaturePresentation(method.getSignature(PsiSubstitutor.EMPTY));
                String superPresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
                String superQName = getQNameOfMember(superMethod);

                holder.createErrorAnnotation(
                    GrHighlightUtil.getMethodHeaderTextRange(method),
                    GroovyLocalize.method0CannotOverrideMethod1In2OverriddenMethodIsFinal(current, superPresentation, superQName).get()
                );

                return;
            }

            String currentModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
            String superModifier = VisibilityUtil.getVisibilityModifier(superMethod.getModifierList());

            if (PsiModifier.PUBLIC.equals(superModifier)
                && (PsiModifier.PROTECTED.equals(currentModifier) || PsiModifier.PRIVATE.equals(currentModifier))
                || PsiModifier.PROTECTED.equals(superModifier) && PsiModifier.PRIVATE.equals(currentModifier)) {
                String currentPresentation = GroovyPresentationUtil.getSignaturePresentation(method.getSignature(PsiSubstitutor.EMPTY));
                String superPresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
                String superQName = getQNameOfMember(superMethod);

                PsiElement modifier = PsiUtil.findModifierInList(method.getModifierList(), currentModifier);
                holder.createErrorAnnotation(
                    modifier != null ? modifier : method.getNameIdentifierGroovy(),
                    GroovyLocalize.method0CannotHaveWeakerAccessPrivileges1Than2In34(
                        currentPresentation,
                        currentModifier,
                        superPresentation,
                        superQName,
                        superModifier
                    ).get()
                );
            }
        }
    }

    @RequiredReadAction
    private static void checkMethodWithTypeParamsShouldHaveReturnType(AnnotationHolder holder, GrMethod method) {
        PsiTypeParameterList parameterList = method.getTypeParameterList();
        if (parameterList != null) {
            GrTypeElement typeElement = method.getReturnTypeElementGroovy();
            if (typeElement == null) {
                TextRange parameterListTextRange = parameterList.getTextRange();
                TextRange range = new TextRange(
                    parameterListTextRange.getEndOffset(),
                    parameterListTextRange.getEndOffset() + 1
                );
                holder.createErrorAnnotation(range, GroovyLocalize.methodWithTypeParametersShouldHaveReturnType().get());
            }
        }
    }

    @RequiredReadAction
    private static void checkOptionalParametersInAbstractMethod(AnnotationHolder holder, GrMethod method) {
        if (!method.isAbstract()) {
            return;
        }

        for (GrParameter parameter : method.getParameters()) {
            GrExpression initializerGroovy = parameter.getInitializerGroovy();
            if (initializerGroovy != null) {
                PsiElement assignOperator = parameter.getNameIdentifierGroovy();
                TextRange textRange = new TextRange(
                    assignOperator.getTextRange().getEndOffset(),
                    initializerGroovy.getTextRange().getEndOffset()
                );
                holder.createErrorAnnotation(textRange, GroovyLocalize.defaultInitializersAreNotAllowedInAbstractMethod().get());
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private static PsiMethod getDefaultConstructor(PsiClass clazz) {
        String className = clazz.getName();
        if (className == null) {
            return null;
        }
        PsiMethod[] byName = clazz.findMethodsByName(className, true);
        if (byName.length == 0) {
            return null;
        }
        Outer:
        for (PsiMethod method : byName) {
            if (method.getParameterList().getParametersCount() == 0) {
                return method;
            }
            if (!(method instanceof GrMethod grMethod)) {
                continue;
            }
            GrParameter[] parameters = grMethod.getParameterList().getParameters();

            for (GrParameter parameter : parameters) {
                if (!parameter.isOptional()) {
                    continue Outer;
                }
            }
            return method;
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public void visitVariable(GrVariable variable) {
        checkName(variable);

        if (variable.getParent() instanceof GrForInClause forInClause) {
            PsiElement delimiter = forInClause.getDelimiter();
            if (delimiter.getNode().getElementType() == GroovyTokenTypes.mCOLON) {
                GrTypeElement typeElement = variable.getTypeElementGroovy();
                GrModifierList modifierList = variable.getModifierList();
                if (typeElement == null && StringUtil.isEmptyOrSpaces(modifierList.getText())) {
                    Annotation annotation = myHolder.createErrorAnnotation(
                        variable.getNameIdentifierGroovy(),
                        GroovyLocalize.javaStyleForEachStatementRequiresATypeDeclaration().get()
                    );
                    annotation.registerFix(new ReplaceDelimiterFix());
                }
            }
        }

        PsiNamedElement duplicate = ResolveUtil.findDuplicate(variable);

        if (duplicate instanceof GrVariable
            && (variable instanceof GrField || ResolveUtil.isScriptField(variable) || !(duplicate instanceof GrField))) {
            LocalizeValue message = duplicate instanceof GrField
                ? GroovyLocalize.fieldAlreadyDefined(variable.getName())
                : GroovyLocalize.variableAlreadyDefined(variable.getName());
            myHolder.createErrorAnnotation(
                variable.getNameIdentifierGroovy(),
                GroovyBundle.message(message.get(), variable.getName())
            );
        }

        PsiType type = variable.getDeclaredType();
        if (type instanceof PsiEllipsisType && !isLastParameter(variable)) {
            TextRange range = getEllipsisRange(variable);
            if (range == null) {
                range = getTypeRange(variable);
            }
            if (range != null) {
                myHolder.createErrorAnnotation(range, GroovyLocalize.ellipsisTypeIsNotAllowedHere().get());
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private static TextRange getEllipsisRange(GrVariable variable) {
        if (variable instanceof GrParameter parameter) {
            PsiElement dots = parameter.getEllipsisDots();
            if (dots != null) {
                return dots.getTextRange();
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static TextRange getTypeRange(GrVariable variable) {
        GrTypeElement typeElement = variable.getTypeElementGroovy();
        if (typeElement == null) {
            return null;
        }

        PsiElement sibling = typeElement.getNextSibling();
        if (sibling != null && sibling.getNode().getElementType() == GroovyTokenTypes.mTRIPLE_DOT) {
            return new TextRange(typeElement.getTextRange().getStartOffset(), sibling.getTextRange().getEndOffset());
        }

        return typeElement.getTextRange();
    }


    private static boolean isLastParameter(PsiVariable variable) {
        if (variable instanceof PsiParameter && variable.getParent() instanceof PsiParameterList paramList) {
            PsiParameter[] parameters = paramList.getParameters();

            return parameters.length > 0 && parameters[parameters.length - 1] == variable;
        }
        return false;
    }

    private void checkName(GrVariable variable) {
        if (!"$".equals(variable.getName())) {
            return;
        }
        myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyLocalize.incorrectVariableName().get());
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression expression) {
        GrExpression lValue = expression.getLValue();
        if (!PsiUtil.mightBeLValue(lValue)) {
            myHolder.createErrorAnnotation(lValue, GroovyLocalize.invalidLvalue().get());
        }
    }

    @Override
    public void visitReturnStatement(GrReturnStatement returnStatement) {
        GrExpression value = returnStatement.getReturnValue();
        if (value != null) {
            PsiType type = value.getType();
            if (type != null) {
                GrParameterListOwner owner = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class, GrClosableBlock.class);
                if (owner instanceof PsiMethod method) {
                    if (method.isConstructor()) {
                        myHolder.createErrorAnnotation(value, GroovyLocalize.cannotReturnFromConstructor().get());
                    }
                    else {
                        PsiType methodType = method.getReturnType();
                        if (methodType != null && PsiType.VOID.equals(methodType)) {
                            myHolder.createErrorAnnotation(value, GroovyLocalize.cannotReturnFromVoidMethod().get());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitTypeParameterList(GrTypeParameterList list) {
        PsiElement parent = list.getParent();
        if (parent instanceof GrMethod method && method.isConstructor()
            || parent instanceof GrEnumTypeDefinition
            || parent instanceof GrAnnotationTypeDefinition) {
            myHolder.createErrorAnnotation(list, GroovyLocalize.typeParametersAreUnexpected().get());
        }
    }

    @Override
    @RequiredReadAction
    public void visitListOrMap(GrListOrMap listOrMap) {
        PsiReference constructorReference = listOrMap.getReference();
        if (constructorReference instanceof LiteralConstructorReference literalConstructorRef
            && literalConstructorRef.getConstructedClassType() != null) {
            PsiElement startToken = listOrMap.getFirstChild();
            if (startToken != null && startToken.getNode().getElementType() == GroovyTokenTypes.mLBRACK) {
                myHolder.createInfoAnnotation(startToken, null).setTextAttributes(GroovySyntaxHighlighter.LITERAL_CONVERSION);
            }
            PsiElement endToken = listOrMap.getLastChild();
            if (endToken != null && endToken.getNode().getElementType() == GroovyTokenTypes.mRBRACK) {
                myHolder.createInfoAnnotation(endToken, null).setTextAttributes(GroovySyntaxHighlighter.LITERAL_CONVERSION);
            }
        }

        GrNamedArgument[] namedArguments = listOrMap.getNamedArguments();
        GrExpression[] expressionArguments = listOrMap.getInitializers();

        if (namedArguments.length != 0 && expressionArguments.length != 0) {
            myHolder.createErrorAnnotation(
                listOrMap,
                GroovyLocalize.collectionLiteralContainsNamedArgumentAndExpressionItems().get()
            );
        }

        checkNamedArgs(namedArguments, false);
    }

    @Override
    @RequiredReadAction
    public void visitClassTypeElement(GrClassTypeElement typeElement) {
        super.visitClassTypeElement(typeElement);

        GrCodeReferenceElement ref = typeElement.getReferenceElement();
        GrTypeArgumentList argList = ref.getTypeArgumentList();
        if (argList == null) {
            return;
        }

        GrTypeElement[] elements = argList.getTypeArgumentElements();
        for (GrTypeElement element : elements) {
            checkTypeArgForPrimitive(element, GroovyLocalize.primitiveTypeParametersAreNotAllowed().get());
        }
    }

    @Override
    public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        if (refElement.resolve() instanceof PsiClass psiClass
            && (psiClass.isAnnotationType() || GrAnnotationCollector.findAnnotationCollector(psiClass) != null
            && refElement.getParent() instanceof GrAnnotation)) {
            myHolder.createInfoAnnotation(refElement, null).setTextAttributes(GroovySyntaxHighlighter.ANNOTATION);
        }
    }

    @Override
    public void visitTypeElement(GrTypeElement typeElement) {
        PsiElement parent = typeElement.getParent();
        if (!(parent instanceof GrMethod method)) {
            return;
        }

        if (parent instanceof GrAnnotationMethod) {
            checkAnnotationAttributeType(typeElement, myHolder);
        }
        else if (method.isConstructor()) {
            myHolder.createErrorAnnotation(typeElement, GroovyLocalize.constructorsCannotHaveReturnType().get());
        }
        else {
            checkMethodReturnType(method, typeElement, myHolder);
        }
    }

    @Override
    @RequiredReadAction
    public void visitModifierList(GrModifierList modifierList) {
        PsiElement parent = modifierList.getParent();
        if (parent instanceof GrMethod method) {
            checkMethodDefinitionModifiers(myHolder, method);
        }
        else if (parent instanceof GrTypeDefinition typeDef) {
            checkTypeDefinitionModifiers(myHolder, typeDef);
        }
        else if (parent instanceof GrVariableDeclaration varDeclaration && parent.getParent() instanceof GrTypeDefinition) {
            checkFieldModifiers(myHolder, varDeclaration);
        }
        else if (parent instanceof GrClassInitializer) {
            checkClassInitializerModifiers(myHolder, modifierList);
        }
    }

    private static void checkClassInitializerModifiers(AnnotationHolder holder, GrModifierList modifierList) {
        for (GrAnnotation annotation : modifierList.getAnnotations()) {
            holder.createErrorAnnotation(annotation, GroovyLocalize.initializerCannotHaveAnnotations().get());
        }

        for (@GrModifier.GrModifierConstant String modifier : GrModifier.GROOVY_MODIFIERS) {
            if (PsiModifier.STATIC.equals(modifier)) {
                continue;
            }
            checkModifierIsNotAllowed(modifierList, modifier, GroovyLocalize.initializerCannotBe0(modifier).get(), holder);
        }
    }

    @Override
    public void visitClassInitializer(GrClassInitializer initializer) {
        PsiClass aClass = initializer.getContainingClass();
        if (aClass != null && aClass.isInterface()) {
            TextRange range = GrHighlightUtil.getInitializerHeaderTextRange(initializer);
            myHolder.createErrorAnnotation(range, GroovyLocalize.initializersAreNotAllowedInInterface().get());
        }
    }

    @RequiredReadAction
    private static void checkFieldModifiers(AnnotationHolder holder, GrVariableDeclaration fieldDeclaration) {
        GrModifierList modifierList = fieldDeclaration.getModifierList();
        GrField member = (GrField) fieldDeclaration.getVariables()[0];

        checkAccessModifiers(holder, modifierList, member);
        checkDuplicateModifiers(holder, modifierList, member);

        if (modifierList.hasExplicitModifier(PsiModifier.VOLATILE) && modifierList.hasExplicitModifier(PsiModifier.FINAL)) {
            Annotation annotation = holder.createErrorAnnotation(
                modifierList,
                GroovyLocalize.illegalCombinationOfModifiersVolatileAndFinal().get()
            );
            registerFix(
                annotation,
                new GrModifierFix(member, PsiModifier.VOLATILE, true, false, GrModifierFix.MODIFIER_LIST),
                modifierList
            );
            registerFix(
                annotation,
                new GrModifierFix(member, PsiModifier.FINAL, true, false, GrModifierFix.MODIFIER_LIST),
                modifierList
            );
        }

        checkModifierIsNotAllowed(modifierList, PsiModifier.NATIVE, GroovyLocalize.variableCannotBeNative().get(), holder);
        checkModifierIsNotAllowed(modifierList, PsiModifier.ABSTRACT, GroovyLocalize.variableCannotBeAbstract().get(), holder);

        if (member.getContainingClass() instanceof GrInterfaceDefinition) {
            checkModifierIsNotAllowed(
                modifierList,
                PsiModifier.PRIVATE,
                GroovyLocalize.interfaceMembersAreNotAllowedToBe(PsiModifier.PRIVATE).get(),
                holder
            );
            checkModifierIsNotAllowed(
                modifierList,
                PsiModifier.PROTECTED,
                GroovyLocalize.interfaceMembersAreNotAllowedToBe(PsiModifier.PROTECTED).get(),
                holder
            );
        }
    }

    @RequiredReadAction
    private static void registerFix(Annotation annotation, LocalQuickFix fix, PsiElement place) {
        InspectionManager manager = InspectionManager.getInstance(place.getProject());
        assert !place.getTextRange().isEmpty() : place.getContainingFile().getName();

        ProblemDescriptor descriptor = manager.createProblemDescriptor(
            place,
            place,
            annotation.getMessage().get(),
            annotation.getHighlightType(),
            true,
            LocalQuickFix.EMPTY_ARRAY
        );
        TextRange range = TextRange.create(annotation.getStartOffset(), annotation.getEndOffset());
        annotation.registerFix(fix, range, null, descriptor);
    }

    private static void checkModifierIsNotAllowed(
        @Nonnull GrModifierList modifierList,
        @Nonnull @GrModifier.GrModifierConstant String modifier,
        @Nullable String message,
        @Nonnull AnnotationHolder holder
    ) {
        checkModifierIsNotAllowedImpl(modifierList, modifier, message, holder, false);
    }

    @RequiredReadAction
    private static void checkModifierIsNotAllowedImpl(
        @Nonnull GrModifierList modifierList,
        @Nonnull @GrModifier.GrModifierConstant String modifier,
        @Nullable String message,
        @Nonnull AnnotationHolder holder,
        boolean explicit
    ) {
        if (explicit ? modifierList.hasModifierProperty(modifier) : modifierList.hasExplicitModifier(modifier)) {
            PsiElement modifierOrList = getModifierOrList(modifierList, modifier);
            Annotation annotation = holder.createErrorAnnotation(modifierOrList, message);
            registerFix(
                annotation,
                new GrModifierFix((PsiMember) modifierList.getParent(), modifier, true, false, GrModifierFix.MODIFIER_LIST),
                modifierList
            );
        }
    }

    private static void checkAnnotationAttributeType(GrTypeElement element, AnnotationHolder holder) {
        if (element instanceof GrBuiltInTypeElement) {
            return;
        }

        if (element instanceof GrArrayTypeElement arrayTypeElem) {
            checkAnnotationAttributeType(arrayTypeElem.getComponentTypeElement(), holder);
            return;
        }
        else if (element instanceof GrClassTypeElement classTypeElem
            && classTypeElem.getReferenceElement().resolve() instanceof PsiClass resolvedClass) {
            if (CommonClassNames.JAVA_LANG_STRING.equals(resolvedClass.getQualifiedName())) {
                return;
            }
            if (CommonClassNames.JAVA_LANG_CLASS.equals(resolvedClass.getQualifiedName())) {
                return;
            }
            if (resolvedClass.isAnnotationType()) {
                return;
            }
            if (resolvedClass.isEnum()) {
                return;
            }
        }

        holder.createErrorAnnotation(element, GroovyLocalize.unexpectedAttributeType0(element.getType()).get());
    }

    static void checkMethodReturnType(PsiMethod method, PsiElement toHighlight, AnnotationHolder holder) {
        HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
        List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

        PsiType returnType = signature.getSubstitutor().substitute(method.getReturnType());

        for (HierarchicalMethodSignature superMethodSignature : superSignatures) {
            PsiMethod superMethod = superMethodSignature.getMethod();
            PsiType declaredReturnType = superMethod.getReturnType();
            PsiType superReturnType = superMethodSignature.getSubstitutor().substitute(declaredReturnType);
            if (superReturnType == PsiType.VOID && method instanceof GrMethod grMethod && grMethod.getReturnTypeElementGroovy() == null) {
                return;
            }
            if (superMethodSignature.isRaw()) {
                superReturnType = TypeConversionUtil.erasure(declaredReturnType);
            }
            if (returnType == null || superReturnType == null || method == superMethod) {
                continue;
            }
            PsiClass superClass = superMethod.getContainingClass();
            if (superClass == null) {
                continue;
            }
            String highlightInfo = checkSuperMethodSignature(superMethod, superMethodSignature, superReturnType,
                method, signature, returnType
            );
            if (highlightInfo != null) {
                holder.createErrorAnnotation(toHighlight, highlightInfo);
                return;
            }
        }
    }

    @Nullable
    private static String checkSuperMethodSignature(
        @Nonnull PsiMethod superMethod,
        @Nonnull MethodSignatureBackedByPsiMethod superMethodSignature,
        @Nonnull PsiType superReturnType,
        @Nonnull PsiMethod method,
        @Nonnull MethodSignatureBackedByPsiMethod methodSignature,
        @Nonnull PsiType returnType
    ) {
        PsiType substitutedSuperReturnType = substituteSuperReturnType(superMethodSignature, methodSignature,
            superReturnType
        );

        if (returnType.equals(substitutedSuperReturnType)) {
            return null;
        }

        PsiType rawReturnType = TypeConversionUtil.erasure(returnType);
        PsiType rawSuperReturnType = TypeConversionUtil.erasure(substitutedSuperReturnType);

        if (returnType instanceof PsiClassType && substitutedSuperReturnType instanceof PsiClassType) {
            if (TypeConversionUtil.isAssignable(rawSuperReturnType, rawReturnType)) {
                return null;
            }
        }
        else if (returnType instanceof PsiArrayType && superReturnType instanceof PsiArrayType) {
            if (rawReturnType.equals(rawSuperReturnType)) {
                return null;
            }
        }

        String qName = getQNameOfMember(method);
        String baseQName = getQNameOfMember(superMethod);
        String presentation = returnType.getCanonicalText() + " " +
            GroovyPresentationUtil.getSignaturePresentation(methodSignature);
        String basePresentation = superReturnType.getCanonicalText() + " " +
            GroovyPresentationUtil.getSignaturePresentation(superMethodSignature);
        return GroovyLocalize.returnTypeIsIncompatible(presentation, qName, basePresentation, baseQName).get();
    }

    @Nonnull
    private static PsiType substituteSuperReturnType(
        @Nonnull MethodSignatureBackedByPsiMethod superMethodSignature,
        @Nonnull MethodSignatureBackedByPsiMethod methodSignature,
        @Nonnull PsiType superReturnType
    ) {
        PsiType substitutedSuperReturnType;
        if (!superMethodSignature.isRaw() && superMethodSignature.equals(methodSignature)) { //see 8.4.5
            PsiSubstitutor unifyingSubstitutor =
                MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
            substitutedSuperReturnType = unifyingSubstitutor == null
                ? superReturnType
                : unifyingSubstitutor.substitute(superMethodSignature.getSubstitutor().substitute(superReturnType));
        }
        else {
            substitutedSuperReturnType = TypeConversionUtil.erasure(superReturnType);
        }
        return substitutedSuperReturnType;
    }

    @Nonnull
    private static String getQNameOfMember(@Nonnull PsiMember member) {
        PsiClass aClass = member.getContainingClass();
        return getQName(aClass);
    }

    @Nonnull
    private static String getQName(@Nullable PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass anonymousClass) {
            return GroovyLocalize.anonymousClassDerivedFrom0(anonymousClass.getBaseClassType().getCanonicalText()).get();
        }
        if (aClass != null) {
            String qname = aClass.getQualifiedName();
            if (qname != null) {
                return qname;
            }
        }
        return "<null>";
    }

    @RequiredReadAction
    private void checkTypeArgForPrimitive(@Nullable GrTypeElement element, String message) {
        if (element == null || !(element.getType() instanceof PsiPrimitiveType)) {
            return;
        }

        Annotation annotation = myHolder.createErrorAnnotation(element, message);
        registerFix(annotation, new GrReplacePrimitiveTypeWithWrapperFix(element), element);
    }

    @Override
    @RequiredReadAction
    public void visitWildcardTypeArgument(GrWildcardTypeArgument wildcardTypeArgument) {
        super.visitWildcardTypeArgument(wildcardTypeArgument);

        checkTypeArgForPrimitive(
            wildcardTypeArgument.getBoundTypeElement(),
            GroovyLocalize.primitiveBoundTypesAreNotAllowed().get()
        );
    }

    private void highlightNamedArgs(GrNamedArgument[] namedArguments) {
        for (GrNamedArgument namedArgument : namedArguments) {
            GrArgumentLabel label = namedArgument.getLabel();
            if (label != null && label.getExpression() == null
                && label.getNameElement().getNode().getElementType() != GroovyTokenTypes.mSTAR) {
                myHolder.createInfoAnnotation(label, null).setTextAttributes(GroovySyntaxHighlighter.MAP_KEY);
            }
        }
    }

    private void checkNamedArgs(GrNamedArgument[] namedArguments, boolean forArgList) {
        highlightNamedArgs(namedArguments);

        MultiMap<String, GrArgumentLabel> map = new MultiMap<>();
        for (GrNamedArgument element : namedArguments) {
            GrArgumentLabel label = element.getLabel();
            if (label != null) {
                String name = label.getName();
                if (name != null) {
                    map.putValue(name, label);
                }
            }
        }

        for (String key : map.keySet()) {
            List<GrArgumentLabel> arguments = (List<GrArgumentLabel>) map.get(key);
            if (arguments.size() > 1) {
                for (int i = 1; i < arguments.size(); i++) {
                    GrArgumentLabel label = arguments.get(i);
                    if (forArgList) {
                        myHolder.createErrorAnnotation(label, GroovyLocalize.duplicatedNamedParameter(key).get());
                    }
                    else {
                        myHolder.createWarningAnnotation(label, GroovyLocalize.duplicateElementInTheMap().get());
                    }
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitNewExpression(GrNewExpression newExpression) {
        GrTypeArgumentList constructorTypeArguments = newExpression.getConstructorTypeArguments();
        if (constructorTypeArguments != null) {
            myHolder.createErrorAnnotation(
                constructorTypeArguments,
                GroovyLocalize.groovyDoesNotSupportConstructorTypeArguments().get()
            );
        }

        GrTypeElement typeElement = newExpression.getTypeElement();

        if (typeElement instanceof GrBuiltInTypeElement && newExpression.getArrayCount() == 0) {
            myHolder.createErrorAnnotation(typeElement, GroovyBundle.message("create.instance.of.built-in.type"));
        }

        if (newExpression.getArrayCount() > 0) {
            return;
        }

        GrCodeReferenceElement refElement = newExpression.getReferenceElement();
        if (refElement == null) {
            return;
        }

        PsiElement element = refElement.resolve();
        if (element instanceof PsiClass clazz) {
            if (clazz.isAbstract()) {
                if (newExpression.getAnonymousClassDefinition() == null) {
                    LocalizeValue message = clazz.isInterface()
                        ? GroovyLocalize.cannotInstantiateInterface(clazz.getName())
                        : GroovyLocalize.cannotInstantiateAbstractClass(clazz.getName());
                    myHolder.createErrorAnnotation(refElement, message.get());
                }
                return;
            }
            if (newExpression.getQualifier() != null && clazz.isStatic()) {
                myHolder.createErrorAnnotation(newExpression, GroovyLocalize.qualifiedNewOfStaticClass().get());
            }
        }
    }

    private static boolean checkDiamonds(GrCodeReferenceElement refElement, AnnotationHolder holder) {
        GrTypeArgumentList typeArgumentList = refElement.getTypeArgumentList();
        if (typeArgumentList == null) {
            return true;
        }

        if (!typeArgumentList.isDiamond()) {
            return true;
        }

        GroovyConfigUtils configUtils = GroovyConfigUtils.getInstance();
        if (!configUtils.isVersionAtLeast(refElement, GroovyConfigUtils.GROOVY1_8)) {
            String message = GroovyLocalize.diamondsAreNotAllowedInGroovy0(configUtils.getSDKVersion(refElement)).get();
            holder.createErrorAnnotation(typeArgumentList, message);
        }
        return false;
    }

    @Override
    public void visitArgumentList(GrArgumentList list) {
        checkNamedArgs(list.getNamedArguments(), true);
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
        GroovyResolveResult resolveResult = invocation.advancedResolve();
        if (resolveResult.getElement() == null) {
            GroovyResolveResult[] results = invocation.multiResolveGroovy(false);
            GrArgumentList argList = invocation.getArgumentList();
            if (results.length > 0) {
                LocalizeValue message = GroovyLocalize.ambiguousConstructorCall();
                myHolder.createWarningAnnotation(argList, message.get());
            }
            else {
                PsiClass clazz = invocation.getDelegatedClass();
                if (clazz != null) {
                    //default constructor invocation
                    PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invocation.getInvokedExpression(), true);
                    if (argumentTypes != null && argumentTypes.length > 0) {
                        LocalizeValue message = GroovyLocalize.cannotApplyDefaultConstructor(clazz.getName());
                        myHolder.createWarningAnnotation(argList, message.get());
                    }
                }
            }
        }
    }

    @Override
    public void visitBreakStatement(GrBreakStatement breakStatement) {
        checkFlowInterruptStatement(breakStatement, myHolder);
    }

    @Override
    public void visitContinueStatement(GrContinueStatement continueStatement) {
        checkFlowInterruptStatement(continueStatement, myHolder);
    }

    @Override
    public void visitPackageDefinition(GrPackageDefinition packageDefinition) {
        GrModifierList modifierList = packageDefinition.getAnnotationList();
        checkAnnotationList(myHolder, modifierList, GroovyLocalize.packageDefinitionCannotHaveModifiers().get());
    }

    @Override
    @RequiredReadAction
    public void visitClosure(GrClosableBlock closure) {
        super.visitClosure(closure);
        if (!closure.hasParametersSection() && !followsError(closure) && isClosureAmbiguous(closure)) {
            myHolder.createErrorAnnotation(closure, GroovyLocalize.ambiguousCodeBlock().get());
        }

        if (TypeInferenceHelper.isTooComplexTooAnalyze(closure)) {
            int startOffset = closure.getTextRange().getStartOffset();
            int endOffset;
            PsiElement arrow = closure.getArrow();
            if (arrow != null) {
                endOffset = arrow.getTextRange().getEndOffset();
            }
            else {
                Document document = PsiDocumentManager.getInstance(closure.getProject()).getDocument(closure.getContainingFile());
                if (document == null) {
                    return;
                }
                String text = document.getText();
                endOffset = Math.min(closure.getTextRange().getEndOffset(), text.indexOf('\n', startOffset));
            }
            myHolder.createWeakWarningAnnotation(
                new TextRange(startOffset, endOffset),
                GroovyLocalize.closureIsTooComplexToAnalyze().get()
            );
        }
    }

    /**
     * for example if (!(a inst)) {}
     * ^
     * we are here
     */
    @RequiredReadAction
    private static boolean followsError(GrClosableBlock closure) {
        PsiElement prev = closure.getPrevSibling();
        return prev instanceof PsiErrorElement || prev instanceof PsiWhiteSpace && prev.getPrevSibling() instanceof PsiErrorElement;
    }

    @RequiredReadAction
    private static boolean isClosureAmbiguous(GrClosableBlock closure) {
        PsiElement place = closure;
        while (true) {
            PsiElement parent = place.getParent();
            if (parent == null || parent instanceof GrUnAmbiguousClosureContainer) {
                return false;
            }

            if (PsiUtil.isExpressionStatement(place)) {
                return true;
            }
            if (parent.getFirstChild() != place) {
                return false;
            }
            place = parent;
        }
    }

    @Override
    @RequiredReadAction
    public void visitLiteralExpression(GrLiteral literal) {
        IElementType elementType = literal.getFirstChild().getNode().getElementType();
        if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL) {
            checkStringLiteral(literal);
        }
        else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
            checkRegexLiteral(literal.getFirstChild());
        }
    }

    @Override
    @RequiredReadAction
    public void visitRegexExpression(GrRegex regex) {
        checkRegexLiteral(regex);
    }

    @RequiredReadAction
    private void checkRegexLiteral(PsiElement regex) {
        String text = regex.getText();
        String quote = GrStringUtil.getStartQuote(text);

        GroovyConfigUtils config = GroovyConfigUtils.getInstance();

        if ("$/".equals(quote) && !config.isVersionAtLeast(regex, GroovyConfigUtils.GROOVY1_8)) {
            myHolder.createErrorAnnotation(
                regex,
                GroovyLocalize.dollarSlashStringsAreNotAllowedIn0(config.getSDKVersion(regex)).get()
            );
        }


        String[] parts;
        if (regex instanceof GrRegex grRegex) {
            parts = grRegex.getTextParts();
        }
        else {
            //noinspection ConstantConditions
            parts = new String[]{regex.getFirstChild().getNextSibling().getText()};
        }

        for (String part : parts) {
            if (!GrStringUtil.parseRegexCharacters(part, new StringBuilder(part.length()), null, regex.getText().startsWith("/"))) {
                myHolder.createErrorAnnotation(regex, GroovyLocalize.illegalEscapeCharacterInStringLiteral().get());
                return;
            }
        }

        if ("/".equals(quote) && !config.isVersionAtLeast(regex, GroovyConfigUtils.GROOVY1_8)) {
            if (text.contains("\n") || text.contains("\r")) {
                myHolder.createErrorAnnotation(
                    regex,
                    GroovyLocalize.multilineSlashyStringsAreNotAllowedInGroovy0(config.getSDKVersion(regex)).get()
                );
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitGStringExpression(GrString gstring) {
        for (GrStringContent part : gstring.getContents()) {
            String text = part.getText();
            if (!GrStringUtil.parseStringCharacters(text, new StringBuilder(text.length()), null)) {
                myHolder.createErrorAnnotation(part, GroovyLocalize.illegalEscapeCharacterInStringLiteral().get());
                return;
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitGStringInjection(GrStringInjection injection) {
        if (((GrString) injection.getParent()).isPlainString()) {
            if (StringUtil.indexOf(injection.getText(), '\n') != -1) {
                myHolder.createErrorAnnotation(injection, GroovyLocalize.injectionShouldNotContainLineFeeds().get());
            }
        }
    }

    @RequiredReadAction
    private void checkStringLiteral(PsiElement literal) {
        InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(literal.getProject());
        String text;
        if (injectedLanguageManager.isInjectedFragment(literal.getContainingFile())) {
            text = injectedLanguageManager.getUnescapedText(literal);
        }
        else {
            text = literal.getText();
        }
        assert text != null;

        StringBuilder builder = new StringBuilder(text.length());
        String quote = GrStringUtil.getStartQuote(text);
        if (quote.isEmpty()) {
            return;
        }

        String substring = text.substring(quote.length());
        if (!GrStringUtil.parseStringCharacters(substring, new StringBuilder(text.length()), null)) {
            myHolder.createErrorAnnotation(literal, GroovyLocalize.illegalEscapeCharacterInStringLiteral().get());
            return;
        }

        int[] offsets = new int[substring.length() + 1];
        boolean result = GrStringUtil.parseStringCharacters(substring, builder, offsets);
        LOG.assertTrue(result);
        if (!builder.toString().endsWith(quote) || substring.charAt(offsets[builder.length() - quote.length()]) == '\\') {
            myHolder.createErrorAnnotation(literal, GroovyLocalize.stringEndExpected().get());
        }
    }

    @Override
    @RequiredReadAction
    public void visitForInClause(GrForInClause forInClause) {
        GrVariable var = forInClause.getDeclaredVariable();
        if (var == null) {
            return;
        }
        GrModifierList modifierList = var.getModifierList();
        if (modifierList == null) {
            return;
        }
        PsiElement[] modifiers = modifierList.getModifiers();
        for (PsiElement modifier : modifiers) {
            if (modifier instanceof PsiAnnotation) {
                continue;
            }
            String modifierText = modifier.getText();
            if (PsiModifier.FINAL.equals(modifierText)) {
                continue;
            }
            if (GrModifier.DEF.equals(modifierText)) {
                continue;
            }
            myHolder.createErrorAnnotation(modifier, GroovyLocalize.notAllowedModifierInForin(modifierText).get());
        }
    }

    @Override
    public void visitFile(GroovyFileBase file) {
        PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null) {
            checkDuplicateMethod(scriptClass, myHolder);
            checkSameNameMethodsWithDifferentAccessModifiers(myHolder, file.getCodeMethods());
        }
    }


    @Override
    public void visitAnnotation(GrAnnotation annotation) {
        AnnotationChecker.checkApplicability(annotation, annotation.getOwner(), myHolder, annotation.getNameReferenceElement());
    }

    @Override
    public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
        AnnotationChecker.checkAnnotationArgumentList((GrAnnotation) annotationArgumentList.getParent(), myHolder);
    }

    @Override
    @RequiredReadAction
    public void visitAnnotationMethod(GrAnnotationMethod annotationMethod) {
        super.visitAnnotationMethod(annotationMethod);

        GrAnnotationMemberValue value = annotationMethod.getDefaultValue();
        if (value == null) {
            return;
        }

        PsiType type = annotationMethod.getReturnType();

        Pair<PsiElement, String> result = CustomAnnotationChecker.checkAnnotationValueByType(value, type, false);
        if (result != null) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, result.getSecond())
                .range(result.getFirst())
                .create();
        }
    }

    @Override
    @RequiredReadAction
    public void visitAnnotationNameValuePair(GrAnnotationNameValuePair nameValuePair) {
        PsiElement identifier = nameValuePair.getNameIdentifierGroovy();
        if (identifier == null && nameValuePair.getParent() instanceof GrAnnotationArgumentList annotationArgList) {
            int count = annotationArgList.getAttributes().length;
            if (count > 1) {
                myHolder.createErrorAnnotation(nameValuePair, GroovyLocalize.attributeNameExpected().get());
            }
        }

        GrAnnotationMemberValue value = nameValuePair.getValue();
        if (value != null) {
            checkAnnotationAttributeValue(value, value);
        }
    }

    @RequiredReadAction
    private boolean checkAnnotationAttributeValue(
        @Nullable GrAnnotationMemberValue value,
        @Nonnull PsiElement toHighlight
    ) {
        if (value == null) {
            return false;
        }

        if (value instanceof GrLiteral) {
            return false;
        }
        if (value instanceof GrClosableBlock) {
            return false;
        }
        if (value instanceof GrAnnotation) {
            return false;
        }

        if (value instanceof GrReferenceExpression refExpr) {
            PsiElement resolved = refExpr.resolve();
            if (resolved instanceof PsiClass) {
                return false;
            }
            if (resolved instanceof PsiEnumConstant) {
                return false;
            }
            if (resolved == null && isClassReference(value)) {
                return false;
            }

            if (resolved instanceof GrAccessorMethod accessorMethod) {
                resolved = accessorMethod.getProperty();
            }
            if (resolved instanceof PsiField field) {
                GrExpression initializer;
                try {
                    if (field instanceof GrField grField) {
                        initializer = grField.getInitializerGroovy();
                    }
                    else {
                        PsiExpression _initializer = field.getInitializer();
                        initializer = _initializer != null
                            ? (GrExpression) ExpressionConverter.getExpression(_initializer, GroovyLanguage.INSTANCE, value.getProject())
                            : null;
                    }
                }
                catch (IncorrectOperationException e) {
                    initializer = null;
                }

                if (initializer != null) {
                    return checkAnnotationAttributeValue(initializer, toHighlight);
                }
            }
        }
        if (value instanceof GrAnnotationArrayInitializer annotationArrayInitializer) {
            for (GrAnnotationMemberValue expression : annotationArrayInitializer.getInitializers()) {
                if (checkAnnotationAttributeValue(expression, toHighlight)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof GrUnaryExpression unaryExpr) {
            IElementType tokenType = unaryExpr.getOperationTokenType();
            if (tokenType == GroovyTokenTypes.mMINUS || tokenType == GroovyTokenTypes.mPLUS) {
                return checkAnnotationAttributeValue(unaryExpr.getOperand(), toHighlight);
            }
        }

        myHolder.createErrorAnnotation(
            toHighlight,
            GroovyLocalize.expected0ToBeInlineConstant(value.getText()).get()
        );
        return true;
    }

    private static boolean isClassReference(GrAnnotationMemberValue value) {
        return value instanceof GrReferenceExpression refExpr
            && "class".equals(refExpr.getReferenceName())
            && refExpr.getQualifier() instanceof GrReferenceExpression qualifierRefExpr
            && qualifierRefExpr.resolve() instanceof PsiClass;
    }

    @Override
    public void visitImportStatement(GrImportStatement importStatement) {
        checkAnnotationList(myHolder, importStatement.getAnnotationList(), GroovyLocalize.importStatementCannotHaveModifiers().get());
    }

    @Override
    public void visitExtendsClause(GrExtendsClause extendsClause) {
        GrTypeDefinition typeDefinition = (GrTypeDefinition) extendsClause.getParent();

        if (typeDefinition.isAnnotationType()) {
            myHolder.createErrorAnnotation(extendsClause, GroovyLocalize.annotationTypesMayNotHaveExtendsClause().get());
        }
        else if (typeDefinition.isTrait()) {
            checkReferenceList(myHolder, extendsClause, IS_TRAIT, GroovyBundle.message("only.traits.expected.here"), null);
        }
        else if (typeDefinition.isInterface()) {
            checkReferenceList(myHolder, extendsClause, IS_INTERFACE, GroovyLocalize.noClassExpectedHere().get(), null);
        }
        else if (typeDefinition.isEnum()) {
            myHolder.createErrorAnnotation(extendsClause, GroovyLocalize.enumsMayNotHaveExtendsClause().get());
        }
        else {
            checkReferenceList(
                myHolder,
                extendsClause,
                IS_NOT_INTERFACE,
                GroovyLocalize.noInterfaceExpectedHere().get(),
                new ChangeExtendsImplementsQuickFix(typeDefinition)
            );
            checkForWildCards(myHolder, extendsClause);
        }
    }

    @Override
    public void visitImplementsClause(GrImplementsClause implementsClause) {
        GrTypeDefinition typeDefinition = (GrTypeDefinition) implementsClause.getParent();

        if (typeDefinition.isAnnotationType()) {
            myHolder.createErrorAnnotation(implementsClause, GroovyLocalize.annotationTypesMayNotHaveImplementsClause().get());
        }
        else if (GrTraitUtil.isInterface(typeDefinition)) {
            myHolder.createErrorAnnotation(implementsClause, GroovyLocalize.noImplementsClauseAllowedForInterface().get())
                .registerFix(new ChangeExtendsImplementsQuickFix(typeDefinition));
        }
        else {
            checkReferenceList(
                myHolder,
                implementsClause,
                IS_INTERFACE,
                GroovyLocalize.noClassExpectedHere().get(),
                new ChangeExtendsImplementsQuickFix(typeDefinition)
            );
            checkForWildCards(myHolder, implementsClause);
        }
    }

    private static void checkReferenceList(
        @Nonnull AnnotationHolder holder,
        @Nonnull GrReferenceList list,
        @Nonnull Predicate<PsiClass> applicabilityCondition,
        @Nonnull String message,
        @Nullable IntentionAction fix
    ) {
        for (GrCodeReferenceElement refElement : list.getReferenceElementsGroovy()) {
            if (refElement.resolve() instanceof PsiClass psiClass && !applicabilityCondition.test(psiClass)) {
                Annotation annotation = holder.createErrorAnnotation(refElement, message);
                if (fix != null) {
                    annotation.registerFix(fix);
                }
            }
        }
    }

    private static void checkFlowInterruptStatement(GrFlowInterruptingStatement statement, AnnotationHolder holder) {
        PsiElement label = statement.getLabelIdentifier();

        if (label != null) {
            GrLabeledStatement resolved = statement.resolveLabel();
            if (resolved == null) {
                holder.createErrorAnnotation(label, GroovyLocalize.undefinedLabel(statement.getLabelName()).get());
            }
        }

        GrStatement targetStatement = statement.findTargetStatement();
        if (targetStatement == null) {
            if (statement instanceof GrContinueStatement && label == null) {
                holder.createErrorAnnotation(statement, GroovyLocalize.continueOutsideLoop().get());
            }
            else if (statement instanceof GrBreakStatement && label == null) {
                holder.createErrorAnnotation(statement, GroovyLocalize.breakOutsideLoopOrSwitch().get());
            }
        }
        if (statement instanceof GrBreakStatement && label != null && findFirstLoop(statement) == null) {
            holder.createErrorAnnotation(statement, GroovyLocalize.breakOutsideLoop().get());
        }
    }

    @Nullable
    private static GrLoopStatement findFirstLoop(GrFlowInterruptingStatement statement) {
        return PsiTreeUtil.getParentOfType(statement, GrLoopStatement.class, true, GrClosableBlock.class,
            GrMember.class, GroovyFile.class
        );
    }

    private static void checkThisOrSuperReferenceExpression(GrReferenceExpression ref, AnnotationHolder holder) {
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement == null) {
            return;
        }

        IElementType elementType = nameElement.getNode().getElementType();
        if (!(elementType == GroovyTokenTypes.kSUPER || elementType == GroovyTokenTypes.kTHIS)) {
            return;
        }

        GrExpression qualifier = ref.getQualifier();
        if (qualifier instanceof GrReferenceExpression refExpr) {
            if (refExpr.resolve() instanceof PsiClass resolvedClass) {
                GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(ref, GrTypeDefinition.class, true, GroovyFile.class);

                if (elementType == GroovyTokenTypes.kSUPER && containingClass != null && GrTraitUtil.isTrait(resolvedClass)) {
                    PsiClassType[] superTypes = containingClass.getSuperTypes();
                    if (ContainerUtil.find(
                        superTypes,
                        type -> ref.getManager().areElementsEquivalent(type.resolve(), resolvedClass)
                    ) != null) {
                        holder.createInfoAnnotation(nameElement, null).setTextAttributes(GroovySyntaxHighlighter.KEYWORD);
                        return; // reference to trait method
                    }
                }

                if (containingClass == null || containingClass.getContainingClass() == null && !containingClass.isAnonymous()) {
                    holder.createErrorAnnotation(
                        ref,
                        GroovyLocalize.qualified0IsAllowedOnlyInNestedOrInnerClasses(nameElement.getText()).get()
                    );
                    return;
                }

                if (PsiTreeUtil.isAncestor(resolvedClass, ref, true)) {
                    if (PsiUtil.hasEnclosingInstanceInScope(resolvedClass, ref, true)) {
                        holder.createInfoAnnotation(nameElement, null).setTextAttributes(GroovySyntaxHighlighter.KEYWORD);
                    }
                }
                else {
                    String qname = resolvedClass.getQualifiedName();
                    assert qname != null;
                    holder.createErrorAnnotation(ref, GroovyLocalize.isNotEnclosingClass(qname).get());
                }
            }
        }
        else if (qualifier == null) {
            if (elementType == GroovyTokenTypes.kSUPER) {
                GrMember container = PsiTreeUtil.getParentOfType(ref, GrMethod.class, GrClassInitializer.class);
                if (container != null && container.isStatic()) {
                    holder.createErrorAnnotation(ref, GroovyLocalize.superCannotBeUsedInStaticContext().get());
                }
            }
        }
    }

    private static void checkGrDocReferenceElement(AnnotationHolder holder, PsiElement element) {
        ASTNode node = element.getNode();
        if (node != null && TokenSets.BUILT_IN_TYPES.contains(node.getElementType())) {
            Annotation annotation = holder.createInfoAnnotation(element, null);
            annotation.setTextAttributes(GroovySyntaxHighlighter.KEYWORD);
        }
    }

    private static void checkAnnotationList(
        AnnotationHolder holder,
        @Nonnull GrModifierList modifierList,
        String message
    ) {
        PsiElement[] modifiers = modifierList.getModifiers();
        for (PsiElement modifier : modifiers) {
            if (!(modifier instanceof PsiAnnotation)) {
                holder.createErrorAnnotation(modifier, message);
            }
        }
    }

    @RequiredReadAction
    private static void checkImplementedMethodsOfClass(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
        if (typeDefinition.isAbstract() || typeDefinition.isAnnotationType() || typeDefinition instanceof GrTypeParameter) {
            return;
        }

        PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(typeDefinition);
        if (abstractMethod == null) {
            return;
        }

        String notImplementedMethodName = abstractMethod.getName();

        TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
        Annotation annotation = holder.createErrorAnnotation(
            range,
            GroovyLocalize.methodIsNotImplemented(notImplementedMethodName).get()
        );
        registerImplementsMethodsFix(typeDefinition, abstractMethod, annotation);
    }

    @RequiredReadAction
    private static void registerImplementsMethodsFix(
        @Nonnull GrTypeDefinition typeDefinition,
        @Nonnull PsiMethod abstractMethod,
        @Nonnull Annotation annotation
    ) {
        if (!OverrideImplementExploreUtil.getMethodsToOverrideImplement(typeDefinition, true).isEmpty()) {
            annotation.registerFix(QuickFixFactory.getInstance().createImplementMethodsFix(typeDefinition));
        }

        if (!JavaPsiFacade.getInstance(typeDefinition.getProject()).getResolveHelper().isAccessible(abstractMethod, typeDefinition, null)) {
            registerFix(
                annotation,
                new GrModifierFix(abstractMethod, PsiModifier.PUBLIC, true, true, GrModifierFix.MODIFIER_LIST_OWNER),
                abstractMethod
            );
            registerFix(
                annotation,
                new GrModifierFix(abstractMethod, PsiModifier.PROTECTED, true, true, GrModifierFix.MODIFIER_LIST_OWNER),
                abstractMethod
            );
        }

        if (!(typeDefinition instanceof GrAnnotationTypeDefinition) && typeDefinition.getModifierList() != null) {
            registerFix(
                annotation,
                new GrModifierFix(typeDefinition, PsiModifier.ABSTRACT, false, true, GrModifierFix.MODIFIER_LIST_OWNER),
                typeDefinition
            );
        }
    }

    private static void checkInnerMethod(AnnotationHolder holder, GrMethod grMethod) {
        PsiElement parent = grMethod.getParent();
        if (parent instanceof GrOpenBlock || parent instanceof GrClosableBlock) {
            holder.createErrorAnnotation(
                grMethod.getNameIdentifierGroovy(),
                GroovyLocalize.innerMethodsAreNotSupported().get()
            );
        }
    }

    @RequiredReadAction
    private static void registerMakeAbstractMethodNotAbstractFix(
        Annotation annotation,
        GrMethod method,
        boolean makeClassAbstract
    ) {
        if (method.getBlock() == null) {
            annotation.registerFix(QuickFixFactory.getInstance().createAddMethodBodyFix(method));
        }
        else {
            annotation.registerFix(QuickFixFactory.getInstance().createDeleteMethodBodyFix(method));
        }
        registerFix(
            annotation,
            new GrModifierFix(method, PsiModifier.ABSTRACT, false, false, GrModifierFix.MODIFIER_LIST_OWNER),
            method
        );
        if (makeClassAbstract) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && containingClass.isAbstract()) {
                registerFix(
                    annotation,
                    new GrModifierFix(containingClass, PsiModifier.ABSTRACT, false, true, GrModifierFix.MODIFIER_LIST_OWNER),
                    containingClass
                );
            }
        }
    }

    @RequiredReadAction
    private static void checkMethodDefinitionModifiers(AnnotationHolder holder, GrMethod method) {
        GrModifierList modifiersList = method.getModifierList();
        checkAccessModifiers(holder, modifiersList, method);
        checkDuplicateModifiers(holder, modifiersList, method);
        checkOverrideAnnotation(holder, modifiersList, method);

        checkModifierIsNotAllowed(
            modifiersList,
            PsiModifier.VOLATILE,
            GroovyLocalize.methodHasIncorrectModifierVolatile().get(),
            holder
        );

        checkForAbstractAndFinalCombination(holder, method, modifiersList);

        //script methods
        boolean isMethodAbstract = modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT);
        if (method.getParent() instanceof GroovyFileBase) {
            if (isMethodAbstract) {
                Annotation annotation = holder.createErrorAnnotation(
                    getModifierOrList(
                        modifiersList,
                        PsiModifier.ABSTRACT
                    ),
                    GroovyLocalize.scriptMethodCannotHaveModifierAbstract().get()
                );
                registerMakeAbstractMethodNotAbstractFix(annotation, method, false);
            }

            checkModifierIsNotAllowed(
                modifiersList,
                PsiModifier.NATIVE,
                GroovyLocalize.scriptCannotHaveModifierNative().get(),
                holder
            );
        }
        //type definition methods
        else if (method.getParent() != null && method.getParent().getParent() instanceof GrTypeDefinition containingTypeDef) {
            if (containingTypeDef.isTrait()) {
                checkModifierIsNotAllowed(
                    modifiersList,
                    PsiModifier.PROTECTED,
                    GroovyBundle.message("trait.method.cannot.be.protected"),
                    holder
                );
            }
            //interface
            else if (containingTypeDef.isInterface()) {
                checkModifierIsNotAllowed(
                    modifiersList,
                    PsiModifier.STATIC,
                    GroovyLocalize.interfaceMustHaveNoStaticMethod().get(),
                    holder
                );
                checkModifierIsNotAllowed(
                    modifiersList,
                    PsiModifier.PRIVATE,
                    GroovyLocalize.interfaceMembersAreNotAllowedToBe(PsiModifier.PRIVATE).get(),
                    holder
                );
                checkModifierIsNotAllowed(
                    modifiersList,
                    PsiModifier.PROTECTED,
                    GroovyLocalize.interfaceMembersAreNotAllowedToBe(PsiModifier.PROTECTED).get(),
                    holder
                );
            }
            else if (containingTypeDef.isAnonymous()) {
                if (isMethodAbstract) {
                    Annotation annotation = holder.createErrorAnnotation(
                        getModifierOrList(
                            modifiersList,
                            PsiModifier.ABSTRACT
                        ),
                        GroovyLocalize.anonymousClassCannotHaveAbstractMethod().get()
                    );
                    registerMakeAbstractMethodNotAbstractFix(annotation, method, false);
                }
            }
            //class
            else {
                PsiModifierList typeDefModifiersList = containingTypeDef.getModifierList();
                LOG.assertTrue(typeDefModifiersList != null, "modifiers list must be not null");

                if (!containingTypeDef.isAbstract() && isMethodAbstract) {
                    Annotation annotation = holder.createErrorAnnotation(
                        modifiersList,
                        GroovyLocalize.onlyAbstractClassCanHaveAbstractMethod().get()
                    );
                    registerMakeAbstractMethodNotAbstractFix(annotation, method, true);
                }
            }

            if (method.isConstructor()) {
                checkModifierIsNotAllowed(
                    modifiersList,
                    PsiModifier.STATIC,
                    GroovyLocalize.constructorCannotHaveStaticModifier().get(),
                    holder
                );
            }
        }

        if (method.hasModifierProperty(PsiModifier.NATIVE) && method.getBlock() != null) {
            Annotation annotation = holder.createErrorAnnotation(
                getModifierOrList(
                    modifiersList,
                    PsiModifier.NATIVE
                ),
                GroovyLocalize.nativeMethodsCannotHaveBody().get()
            );
            registerFix(
                annotation,
                new GrModifierFix((PsiMember) modifiersList.getParent(), PsiModifier.NATIVE, true, false, GrModifierFix.MODIFIER_LIST),
                modifiersList
            );
            annotation.registerFix(QuickFixFactory.getInstance().createDeleteMethodBodyFix(method));
        }
    }

    @RequiredReadAction
    private static void checkForAbstractAndFinalCombination(
        AnnotationHolder holder,
        GrMember member,
        GrModifierList modifiersList
    ) {
        if (member.isFinal() && member.isAbstract()) {
            Annotation annotation = holder.createErrorAnnotation(
                modifiersList,
                GroovyLocalize.illegalCombinationOfModifiersAbstractAndFinal().get()
            );
            registerFix(
                annotation,
                new GrModifierFix(member, PsiModifier.FINAL, false, false, GrModifierFix.MODIFIER_LIST),
                modifiersList
            );
            registerFix(
                annotation,
                new GrModifierFix(member, PsiModifier.ABSTRACT, false, false, GrModifierFix.MODIFIER_LIST),
                modifiersList
            );
        }
    }

    @Nonnull
    private static PsiElement getModifierOrList(
        @Nonnull GrModifierList modifiersList,
        @GrModifier.GrModifierConstant String modifier
    ) {
        PsiElement m = PsiUtil.findModifierInList(modifiersList, modifier);
        return m != null ? m : modifiersList;
    }

    private static void checkOverrideAnnotation(AnnotationHolder holder, GrModifierList list, GrMethod method) {
        PsiAnnotation overrideAnnotation = list.findAnnotation("java.lang.Override");
        if (overrideAnnotation == null) {
            return;
        }
        try {
            MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
            if (superMethod == null) {
                holder.createWarningAnnotation(overrideAnnotation, GroovyLocalize.methodDoesnotOverrideSuper().get());
            }
        }
        catch (IndexNotReadyException ignored) {
            //nothing to do
        }
    }

    @RequiredReadAction
    private static void checkTypeDefinitionModifiers(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
        GrModifierList modifiersList = typeDefinition.getModifierList();

        if (modifiersList == null) {
            return;
        }

        /**** class ****/
        checkAccessModifiers(holder, modifiersList, typeDefinition);
        checkDuplicateModifiers(holder, modifiersList, typeDefinition);

        PsiClassType[] extendsListTypes = typeDefinition.getExtendsListTypes();

        for (PsiClassType classType : extendsListTypes) {
            PsiClass psiClass = classType.resolve();

            if (psiClass != null && psiClass.isFinal()) {
                Annotation annotation = holder.createErrorAnnotation(
                    typeDefinition.getNameIdentifierGroovy(),
                    GroovyLocalize.finalClassCannotBeExtended().get()
                );
                registerFix(
                    annotation,
                    new GrModifierFix(typeDefinition, PsiModifier.FINAL, false, false, GrModifierFix.MODIFIER_LIST_OWNER),
                    typeDefinition
                );
            }
        }

        if (!typeDefinition.isEnum()) {
            checkForAbstractAndFinalCombination(holder, typeDefinition, modifiersList);
        }

        checkModifierIsNotAllowed(
            modifiersList,
            PsiModifier.TRANSIENT,
            GroovyLocalize.modifierTransientNotAllowedHere().get(),
            holder
        );
        checkModifierIsNotAllowed(
            modifiersList,
            PsiModifier.VOLATILE,
            GroovyLocalize.modifierVolatileNotAllowedHere().get(),
            holder
        );

        /**** interface ****/
        if (typeDefinition.isInterface()) {
            checkModifierIsNotAllowed(
                modifiersList,
                PsiModifier.FINAL,
                GroovyLocalize.intarfaceCannotHaveModifierFinal().get(),
                holder
            );
        }
    }

    @RequiredReadAction
    private static void checkDuplicateModifiers(
        AnnotationHolder holder, @Nonnull GrModifierList list,
        PsiMember member
    ) {
        PsiElement[] modifiers = list.getModifiers();
        Set<String> set = new HashSet<>(modifiers.length);
        for (PsiElement modifier : modifiers) {
            if (modifier instanceof GrAnnotation) {
                continue;
            }
            @GrModifier.GrModifierConstant String name = modifier.getText();
            if (set.contains(name)) {
                Annotation annotation = holder.createErrorAnnotation(list, GroovyLocalize.duplicateModifier(name).get());
                registerFix(
                    annotation,
                    new GrModifierFix(member, name, false, false, GrModifierFix.MODIFIER_LIST),
                    list
                );
            }
            else {
                set.add(name);
            }
        }
    }

    @RequiredReadAction
    private static void checkAccessModifiers(
        AnnotationHolder holder,
        @Nonnull GrModifierList modifierList,
        PsiMember member
    ) {
        boolean hasPrivate = modifierList.hasExplicitModifier(PsiModifier.PRIVATE);
        boolean hasPublic = modifierList.hasExplicitModifier(PsiModifier.PUBLIC);
        boolean hasProtected = modifierList.hasExplicitModifier(PsiModifier.PROTECTED);

        if (hasPrivate && hasPublic || hasPrivate && hasProtected || hasPublic && hasProtected) {
            Annotation annotation = holder.createErrorAnnotation(
                modifierList,
                GroovyLocalize.illegalCombinationOfModifiers().get()
            );
            if (hasPrivate) {
                registerFix(
                    annotation,
                    new GrModifierFix(member, PsiModifier.PRIVATE, false, false, GrModifierFix.MODIFIER_LIST),
                    modifierList
                );
            }
            if (hasProtected) {
                registerFix(
                    annotation,
                    new GrModifierFix(member, PsiModifier.PROTECTED, false, false, GrModifierFix.MODIFIER_LIST),
                    modifierList
                );
            }
            if (hasPublic) {
                registerFix(
                    annotation,
                    new GrModifierFix(member, PsiModifier.PUBLIC, false, false, GrModifierFix.MODIFIER_LIST),
                    modifierList
                );
            }
        }
        else if (member instanceof PsiClass
            && member.getContainingClass() == null
            && GroovyConfigUtils.getInstance().isVersionAtLeast(member, GroovyConfigUtils.GROOVY2_0)) {
            checkModifierIsNotAllowed(
                modifierList,
                PsiModifier.PRIVATE,
                GroovyLocalize.topLevelClassMaynotHavePrivateModifier().get(),
                holder
            );
            checkModifierIsNotAllowed(
                modifierList,
                PsiModifier.PROTECTED,
                GroovyLocalize.topLevelClassMaynotHaveProtectedModifier().get(),
                holder
            );
        }
    }

    private static void checkDuplicateMethod(PsiClass clazz, AnnotationHolder holder) {
        MultiMap<MethodSignature, PsiMethod> map = GrClosureSignatureUtil.findRawMethodSignatures(
            clazz.getMethods(),
            clazz
        );
        processMethodDuplicates(map, holder);
    }

    protected static void processMethodDuplicates(MultiMap<MethodSignature, PsiMethod> map, AnnotationHolder holder) {
        for (MethodSignature signature : map.keySet()) {
            Collection<PsiMethod> methods = map.get(signature);
            if (methods.size() > 1) {
                for (Iterator<PsiMethod> iterator = methods.iterator(); iterator.hasNext(); ) {
                    PsiMethod method = iterator.next();
                    if (method instanceof LightElement) {
                        iterator.remove();
                    }
                }

                if (methods.size() < 2) {
                    continue;
                }
                String signaturePresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
                for (PsiMethod method : methods) {
                    //noinspection ConstantConditions
                    holder.createErrorAnnotation(
                        GrHighlightUtil.getMethodHeaderTextRange(method),
                        GroovyLocalize.methodDuplicate(signaturePresentation, method.getContainingClass().getName()).get()
                    );
                }
            }
        }
    }

    @RequiredReadAction
    private static void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
        GroovyConfigUtils configUtils = GroovyConfigUtils.getInstance();
        if (typeDefinition.isAnonymous()) {
            if (!configUtils.isVersionAtLeast(typeDefinition, GroovyConfigUtils.GROOVY1_7)) {
                holder.createErrorAnnotation(
                    typeDefinition.getNameIdentifierGroovy(),
                    GroovyLocalize.anonymousClassesAreNotSupported(configUtils.getSDKVersion(typeDefinition)).get()
                );
            }

            PsiElement superClass = ((PsiAnonymousClass) typeDefinition).getBaseClassReference().resolve();
            if (superClass instanceof GrTypeDefinition typeDef && typeDef.isTrait()) {
                holder.createErrorAnnotation(
                    typeDefinition.getNameIdentifierGroovy(),
                    GroovyBundle.message("anonymous.classes.cannot.be.created.from.traits")
                );
            }
        }
        else if (typeDefinition.isTrait()) {
            if (!configUtils.isVersionAtLeast(typeDefinition, GroovyConfigUtils.GROOVY2_3)) {
                ASTNode keyword = typeDefinition.getNode().findChildByType(GroovyTokenTypes.kTRAIT);
                assert keyword != null;
                holder.createErrorAnnotation(
                    keyword,
                    GroovyBundle.message("traits.are.not.supported.in.groovy.0", configUtils.getSDKVersion(typeDefinition))
                );
            }
        }
        else if (typeDefinition.getContainingClass() != null && !(typeDefinition instanceof GrEnumTypeDefinition)) {
            if (!configUtils.isVersionAtLeast(typeDefinition, GroovyConfigUtils.GROOVY1_7)) {
                holder.createErrorAnnotation(
                    typeDefinition.getNameIdentifierGroovy(),
                    GroovyLocalize.innerClassesAreNotSupported(configUtils.getSDKVersion(typeDefinition)).get()
                );
            }
        }

        if (typeDefinition.isAnnotationType() && typeDefinition.getContainingClass() != null) {
            holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyLocalize.annotationTypeCannotBeInner().get());
        }

        checkDuplicateClass(typeDefinition, holder);

        checkCyclicInheritance(holder, typeDefinition);
    }

    private static void checkCyclicInheritance(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
        PsiClass psiClass = HighlightClassUtil.getCircularClass(typeDefinition, new HashSet<>());
        if (psiClass != null) {
            String qname = psiClass.getQualifiedName();
            assert qname != null;
            holder.createErrorAnnotation(
                GrHighlightUtil.getClassHeaderTextRange(typeDefinition),
                GroovyLocalize.cyclicInheritanceInvolving0(qname).get()
            );
        }
    }

    private static void checkForWildCards(AnnotationHolder holder, @Nullable GrReferenceList clause) {
        if (clause == null) {
            return;
        }
        GrCodeReferenceElement[] elements = clause.getReferenceElementsGroovy();
        for (GrCodeReferenceElement element : elements) {
            GrTypeArgumentList list = element.getTypeArgumentList();
            if (list != null) {
                for (GrTypeElement type : list.getTypeArgumentElements()) {
                    if (type instanceof GrWildcardTypeArgument) {
                        holder.createErrorAnnotation(type, GroovyLocalize.wildcardsAreNotAllowedInExtendsList().get());
                    }
                }
            }
        }
    }

    @RequiredReadAction
    private static void checkDuplicateClass(GrTypeDefinition typeDefinition, AnnotationHolder holder) {
        PsiClass containingClass = typeDefinition.getContainingClass();
        String name = typeDefinition.getName();
        if (containingClass != null) {
            String containingClassName = containingClass.getName();
            if (containingClassName != null && containingClassName.equals(name)) {
                holder.createErrorAnnotation(
                    typeDefinition.getNameIdentifierGroovy(),
                    GroovyLocalize.duplicateInnerClass(name).get()
                );
            }
        }
        String qName = typeDefinition.getQualifiedName();
        if (qName != null) {
            JavaPsiFacade facade = JavaPsiFacade.getInstance(typeDefinition.getProject());
            GlobalSearchScope scope = inferClassScopeForSearchingDuplicates(typeDefinition);
            PsiClass[] classes = facade.findClasses(qName, scope);
            if (classes.length > 1) {
                String packageName = getPackageName(typeDefinition);

                if (!isScriptGeneratedClass(classes)) {
                    holder.createErrorAnnotation(
                        typeDefinition.getNameIdentifierGroovy(),
                        GroovyLocalize.duplicateClass(name, packageName).get()
                    );
                }
                else {
                    holder.createErrorAnnotation(
                        typeDefinition.getNameIdentifierGroovy(),
                        GroovyLocalize.scriptGeneratedWithSameName(qName).get()
                    );
                }
            }
        }
    }

    @RequiredReadAction
    private static GlobalSearchScope inferClassScopeForSearchingDuplicates(GrTypeDefinition typeDefinition) {
        GlobalSearchScope defaultScope = typeDefinition.getResolveScope();

        if (typeDefinition.getContainingFile() instanceof GroovyFile groovyFile && groovyFile.isScript()) {
            Module module = ModuleUtilCore.findModuleForPsiElement(groovyFile);
            if (module != null) {
                return defaultScope.intersectWith(GlobalSearchScope.moduleScope(module));
            }
        }
        return defaultScope;
    }

    private static String getPackageName(GrTypeDefinition typeDefinition) {
        String packageName = "<default package>";
        if (typeDefinition.getContainingFile() instanceof GroovyFile groovyFile) {
            String name = groovyFile.getPackageName();
            if (!name.isEmpty()) {
                packageName = name;
            }
        }
        return packageName;
    }

    private static boolean isScriptGeneratedClass(PsiClass[] allClasses) {
        return allClasses.length == 2 && (allClasses[0] instanceof GroovyScriptClass || allClasses[1] instanceof GroovyScriptClass);
    }
}

