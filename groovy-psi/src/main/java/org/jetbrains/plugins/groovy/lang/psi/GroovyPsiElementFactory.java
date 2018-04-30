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

package org.jetbrains.plugins.groovy.lang.psi;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dimaskin
 */
public abstract class GroovyPsiElementFactory implements JVMElementFactory
{
	@NonNls
	public static final String DUMMY_FILE_NAME = "DUMMY__1234567890_DUMMYYYYYY___";

	public abstract GrCodeReferenceElement createCodeReferenceElementFromClass(PsiClass aClass);

	public abstract GrCodeReferenceElement createCodeReferenceElementFromText(String text);

	public abstract GrReferenceExpression createThisExpression(@Nullable PsiClass psiClass);

	public abstract GrBlockStatement createBlockStatementFromText(String text, @Nullable PsiElement context);

	public abstract GrModifierList createModifierList(CharSequence text);

	public abstract GrCaseSection createSwitchSection(String text);

	public static GroovyPsiElementFactory getInstance(Project project)
	{
		return ServiceManager.getService(project, GroovyPsiElementFactory.class);
	}

	/**
	 * Creates an empty class initializer block.
	 *
	 * @return the created initializer block instance.
	 * @throws IncorrectOperationException in case of an internal error.
	 */
	@Override
	@Nonnull
	public abstract GrClassInitializer createClassInitializer() throws IncorrectOperationException;

	/**
	 * @param qName
	 * @param isStatic
	 * @param isOnDemand
	 * @param alias
	 * @return import statement for given class
	 */
	public abstract GrImportStatement createImportStatementFromText(@Nonnull String qName,
			boolean isStatic,
			boolean isOnDemand,
			@javax.annotation.Nullable String alias);

	public abstract GrImportStatement createImportStatementFromText(@Nonnull String text);

	public abstract GrImportStatement createImportStatement(@Nonnull String qname,
			boolean isStatic,
			boolean isOnDemand,
			@javax.annotation.Nullable String alias,
			@Nullable PsiElement context);

	public abstract PsiElement createWhiteSpace();

	@Nonnull
	public abstract PsiElement createLineTerminator(int length);

	@Nonnull
	public abstract PsiElement createLineTerminator(String text);

	public abstract GrArgumentList createExpressionArgumentList(GrExpression... expressions);

	public abstract GrNamedArgument createNamedArgument(String name, GrExpression expression);

	public abstract GrStatement createStatementFromText(CharSequence text);

	public abstract GrStatement createStatementFromText(CharSequence text, @Nullable PsiElement context);

	public abstract GrBlockStatement createBlockStatement(GrStatement... statements);

	public abstract GrMethodCallExpression createMethodCallByAppCall(GrApplicationStatement callExpr);

	public abstract GrReferenceExpression createReferenceExpressionFromText(String exprText);

	public abstract GrReferenceExpression createReferenceExpressionFromText(String idText, PsiElement context);

	public abstract GrReferenceExpression createReferenceElementForClass(PsiClass clazz);

	public GrCodeReferenceElement createReferenceElementFromText(String refName)
	{
		return createReferenceElementFromText(refName, null);
	}

	public abstract GrCodeReferenceElement createReferenceElementFromText(String refName,
			@Nullable PsiElement context);

	public GrExpression createExpressionFromText(CharSequence exprText)
	{
		return createExpressionFromText(exprText.toString(), null);
	}

	@Override
	@Nonnull
	public abstract GrExpression createExpressionFromText(@Nonnull String exprText, @Nullable PsiElement context);

	public abstract GrVariableDeclaration createFieldDeclaration(String[] modifiers,
			String identifier,
			@Nullable GrExpression initializer,
			@Nullable PsiType type);

	public abstract GrVariableDeclaration createFieldDeclarationFromText(String text);

	public abstract GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers,
			@Nullable GrExpression initializer,
			@Nullable PsiType type,
			String... identifiers);

	public abstract GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers,
			@Nullable String initializer,
			@Nullable PsiType type,
			String... identifiers);

	public abstract GrEnumConstant createEnumConstantFromText(String text);

	@Nonnull
	public abstract PsiElement createReferenceNameFromText(String idText);

	public abstract PsiElement createDocMemberReferenceNameFromText(String idText);

	public abstract GrDocMemberReference createDocMemberReferenceFromText(String className, String text);

	public abstract GrDocReferenceElement createDocReferenceElementFromFQN(String qName);

	public abstract GrTopStatement createTopElementFromText(String text);

	public abstract GrClosableBlock createClosureFromText(String text, @Nullable PsiElement context);

	public GrClosableBlock createClosureFromText(String s) throws IncorrectOperationException
	{
		return createClosureFromText(s, null);
	}

	public GrParameter createParameter(String name,
			@Nullable String typeText,
			@Nullable GroovyPsiElement context) throws IncorrectOperationException
	{
		return createParameter(name, typeText, null, context);
	}

	public abstract GrParameter createParameter(String name,
			@Nullable String typeText,
			@javax.annotation.Nullable String initializer,
			@Nullable GroovyPsiElement context,
			String... modifiers) throws IncorrectOperationException;

	public abstract GrCodeReferenceElement createTypeOrPackageReference(String qName);

	public abstract GrTypeDefinition createTypeDefinition(String text) throws IncorrectOperationException;

	public abstract GrTypeElement createTypeElement(PsiType type) throws IncorrectOperationException;

	@Nonnull
	public GrTypeElement createTypeElement(String typeText) throws IncorrectOperationException
	{
		return createTypeElement(typeText, null);
	}

	public abstract GrTypeElement createTypeElement(String typeText, @Nullable PsiElement context);

	public abstract GrParenthesizedExpression createParenthesizedExpr(GrExpression expression);

	public abstract PsiElement createStringLiteralForReference(String text);

	public abstract PsiElement createModifierFromText(String name);

	public abstract GrCodeBlock createMethodBodyFromText(String text);

	public abstract GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText);

	public abstract GrReferenceElement createPackageReferenceElementFromText(String newPackageName);

	public abstract PsiElement createDotToken(String newDot);

	@Override
	@Nonnull
	public abstract GrMethod createMethodFromText(String methodText, @javax.annotation.Nullable PsiElement context);

	@Nonnull
	@Override
	public abstract GrAnnotation createAnnotationFromText(@Nonnull @NonNls String annotationText,
			@Nullable PsiElement context) throws IncorrectOperationException;

	public abstract GrMethod createMethodFromSignature(String name, GrClosureSignature signature);

	public GrMethod createMethodFromText(CharSequence methodText)
	{
		return createMethodFromText(methodText.toString(), null);
	}

	public abstract GrAnnotation createAnnotationFromText(String annoText);

	public abstract GroovyFile createGroovyFile(CharSequence idText, boolean isPhysical, @Nullable PsiElement context);

	public abstract GrMethod createMethodFromText(String modifier,
			String name,
			@Nullable String type,
			String[] paramTypes,
			@Nullable PsiElement context);

	public abstract GrMethod createConstructorFromText(@Nonnull String constructorName,
			String[] paramTypes,
			String[] paramNames,
			String body,
			@Nullable PsiElement context);

	public GrMethod createConstructorFromText(@Nonnull String constructorName,
			String[] paramTypes,
			String[] paramNames,
			String body)
	{
		return createConstructorFromText(constructorName, paramTypes, paramNames, body, null);
	}

	public abstract GrMethod createConstructorFromText(String constructorName,
			CharSequence constructorText,
			@Nullable PsiElement context);

	@Override
	@Nonnull
	public abstract GrDocComment createDocCommentFromText(@Nonnull String text);

	public abstract GrDocTag createDocTagFromText(String text);

	public abstract GrConstructorInvocation createConstructorInvocation(String text);

	public abstract GrConstructorInvocation createConstructorInvocation(String text, PsiElement context);

	public abstract PsiReferenceList createThrownList(PsiClassType[] exceptionTypes);

	public abstract GrCatchClause createCatchClause(PsiClassType type, String parameterName);

	public abstract GrArgumentList createArgumentList();

	public abstract GrArgumentList createArgumentListFromText(String argListText);

	public abstract GrExtendsClause createExtendsClause();

	public abstract GrImplementsClause createImplementsClause();

	public abstract GrLiteral createLiteralFromValue(@javax.annotation.Nullable Object value);

	@Override
	@Nonnull
	public abstract GrMethod createMethod(@Nonnull @NonNls String name,
			@Nullable PsiType returnType) throws IncorrectOperationException;

	@Override
	@Nonnull
	public abstract GrMethod createMethod(@Nonnull @NonNls String name,
			@Nullable PsiType returnType,
			@Nullable PsiElement context) throws IncorrectOperationException;

	@Nonnull
	@Override
	public abstract GrMethod createConstructor();

	@Nonnull
	@Override
	public abstract GrParameter createParameter(@Nonnull @NonNls String name,
			@Nullable PsiType type) throws IncorrectOperationException;

	@Nonnull
	@Override
	public abstract GrField createField(@Nonnull @NonNls String name,
			@Nonnull PsiType type) throws IncorrectOperationException;

	public abstract GrTraitTypeDefinition createTrait(String name);
}
