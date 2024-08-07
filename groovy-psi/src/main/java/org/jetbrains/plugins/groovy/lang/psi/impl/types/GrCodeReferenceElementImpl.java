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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrCodeReferenceElementImpl extends GrReferenceElementImpl<GrCodeReferenceElement> implements
		GrCodeReferenceElement
{
	private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.types" +
			".GrCodeReferenceElementImpl");

	public GrCodeReferenceElementImpl(@Nonnull ASTNode node)
	{
		super(node);
	}

	@Override
	public PsiElement handleElementRenameSimple(String newElementName) throws IncorrectOperationException
	{
		if(StringUtil.isJavaIdentifier(newElementName))
		{
			return super.handleElementRenameSimple(newElementName);
		}
		else
		{
			throw new IncorrectOperationException("Cannot rename reference to '" + newElementName + "'");
		}
	}

	@Override
	protected GrCodeReferenceElement bindWithQualifiedRef(@Nonnull String qName)
	{
		final GrCodeReferenceElement qualifiedRef = GroovyPsiElementFactory.getInstance(getProject())
				.createTypeOrPackageReference(qName);
		final PsiElement list = getTypeArgumentList();
		if(list != null)
		{
			qualifiedRef.getNode().addChild(list.copy().getNode());
		}
		getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
		return qualifiedRef;
	}

	@Override
	public void accept(GroovyElementVisitor visitor)
	{
		visitor.visitCodeReferenceElement(this);
	}

	public String toString()
	{
		return "Reference element";
	}

	@Override
	public GrCodeReferenceElement getQualifier()
	{
		return (GrCodeReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
	}

	@Override
	public PsiElement getReferenceNameElement()
	{
		return findChildByType(TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS);
	}

	public enum ReferenceKind
	{
		CLASS,
		CLASS_OR_PACKAGE,
		PACKAGE_FQ,
		CLASS_FQ,
		CLASS_OR_PACKAGE_FQ,
		STATIC_MEMBER_FQ,
		CLASS_IN_QUALIFIED_NEW
	}

	@Override
	@Nullable
	public PsiElement resolve()
	{
		ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, false, RESOLVER);
		return results.length == 1 ? results[0].getElement() : null;
	}

	public ReferenceKind getKind(boolean forCompletion)
	{
		if(isClassReferenceForNew())
		{
			return ReferenceKind.CLASS_OR_PACKAGE;
		}

		PsiElement parent = getParent();
		if(parent instanceof GrCodeReferenceElementImpl)
		{
			ReferenceKind parentKind = ((GrCodeReferenceElementImpl) parent).getKind(forCompletion);
			if(parentKind == ReferenceKind.CLASS)
			{
				return ReferenceKind.CLASS_OR_PACKAGE;
			}
			else if(parentKind == ReferenceKind.STATIC_MEMBER_FQ)
			{
				return isQualified() ? ReferenceKind.CLASS_FQ : ReferenceKind.CLASS;
			}
			else if(parentKind == ReferenceKind.CLASS_FQ)
			{
				return ReferenceKind.CLASS_OR_PACKAGE_FQ;
			}
			return parentKind;
		}
		else if(parent instanceof GrPackageDefinition)
		{
			return ReferenceKind.PACKAGE_FQ;
		}
		else if(parent instanceof GrDocReferenceElement)
		{
			return ReferenceKind.CLASS_OR_PACKAGE;
		}
		else if(parent instanceof GrImportStatement)
		{
			final GrImportStatement importStatement = (GrImportStatement) parent;
			if(importStatement.isStatic())
			{
				return importStatement.isOnDemand() ? ReferenceKind.CLASS : ReferenceKind.STATIC_MEMBER_FQ;
			}
			else
			{
				return forCompletion || importStatement.isOnDemand() ? ReferenceKind.CLASS_OR_PACKAGE_FQ :
						ReferenceKind.CLASS_FQ;
			}
		}
		else if(parent instanceof GrNewExpression || parent instanceof GrAnonymousClassDefinition)
		{
			PsiElement newExpr = parent instanceof GrAnonymousClassDefinition ? parent.getParent() : parent;
			assert newExpr instanceof GrNewExpression;
			if(((GrNewExpression) newExpr).getQualifier() != null)
			{
				return ReferenceKind.CLASS_IN_QUALIFIED_NEW;
			}
		}

		return ReferenceKind.CLASS;
	}

	@Override
	@Nonnull
	public String getCanonicalText()
	{
		final ReferenceKind kind = getKind(false);
		switch(kind)
		{
			case CLASS:
			case CLASS_IN_QUALIFIED_NEW:
			case CLASS_OR_PACKAGE:
				final PsiElement target = resolve();
				if(target instanceof PsiClass)
				{
					final PsiClass aClass = (PsiClass) target;
					String name = aClass.getQualifiedName();
					if(name == null)
					{ //parameter types don't have qualified name
						name = aClass.getName();
					}
					final PsiType[] types = getTypeArguments();
					if(types.length == 0)
					{
						return name;
					}

					final StringBuilder buf = new StringBuilder();
					buf.append(name);
					buf.append('<');
					for(int i = 0; i < types.length; i++)
					{
						if(i > 0)
						{
							buf.append(',');
						}
						buf.append(types[i].getCanonicalText());
					}
					buf.append('>');

					return buf.toString();
				}
				else if(target instanceof PsiPackage)
				{
					return ((PsiPackage) target).getQualifiedName();
				}
				else
				{
					LOG.assertTrue(target == null);
					return getTextSkipWhiteSpaceAndComments();
				}

			case CLASS_FQ:
			case CLASS_OR_PACKAGE_FQ:
			case PACKAGE_FQ:
			case STATIC_MEMBER_FQ:
				return getTextSkipWhiteSpaceAndComments();
			default:
				LOG.assertTrue(false);
				return null;
		}
	}

	@Override
	protected boolean bindsCorrectly(PsiElement element)
	{
		if(super.bindsCorrectly(element))
		{
			return true;
		}
		if(element instanceof PsiClass)
		{
			final PsiElement resolved = resolve();
			if(resolved instanceof PsiMethod)
			{
				final PsiMethod method = (PsiMethod) resolved;
				if(method.isConstructor() && getManager().areElementsEquivalent(element, method.getContainingClass()))
				{
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean isFullyQualified()
	{
		switch(getKind(false))
		{
			case PACKAGE_FQ:
			case CLASS_FQ:
			case CLASS_OR_PACKAGE_FQ:
			case STATIC_MEMBER_FQ:
			case CLASS_OR_PACKAGE:
				if(resolve() instanceof PsiPackage)
				{
					return true;
				}
			case CLASS:
			case CLASS_IN_QUALIFIED_NEW:
		}
		final GrCodeReferenceElement qualifier = getQualifier();
		return qualifier != null && ((GrCodeReferenceElementImpl) qualifier).isFullyQualified();
	}

	@Override
	public boolean isReferenceTo(PsiElement element)
	{
		final PsiManager manager = getManager();
		if(element instanceof PsiNamedElement && getParent() instanceof GrImportStatement)
		{
			final GroovyResolveResult[] results = multiResolve(false);
			for(GroovyResolveResult result : results)
			{
				if(manager.areElementsEquivalent(result.getElement(), element))
				{
					return true;
				}
			}
		}
		return manager.areElementsEquivalent(element, resolve());
	}

	@Override
	@Nonnull
	public Object[] getVariants()
	{
		return ArrayUtil.EMPTY_OBJECT_ARRAY;
	}

	private boolean isClassReferenceForNew()
	{
		PsiElement parent = getParent();
		while(parent instanceof GrCodeReferenceElement)
		{
			parent = parent.getParent();
		}
		return parent instanceof GrNewExpression;
	}

	@Override
	public boolean isSoft()
	{
		return false;
	}

	private static class OurResolver implements ResolveCache.PolyVariantResolver<GrCodeReferenceElementImpl>
	{

		@Override
		@Nonnull
		public GroovyResolveResult[] resolve(@Nonnull GrCodeReferenceElementImpl reference, boolean incompleteCode)
		{
			if(reference.getReferenceName() == null)
			{
				return GroovyResolveResult.EMPTY_ARRAY;
			}
			final GroovyResolveResult[] results = _resolve(reference, reference.getManager(),
					reference.getKind(false));
			if(results.length == 0)
			{
				return results;
			}

			List<GroovyResolveResult> imported = new ArrayList<GroovyResolveResult>();
			final PsiType[] args = reference.getTypeArguments();
			for(int i = 0; i < results.length; i++)
			{
				GroovyResolveResult result = results[i];
				final PsiElement element = result.getElement();
				if(element instanceof PsiClass)
				{
					final PsiSubstitutor substitutor = result.getSubstitutor();
					final PsiSubstitutor newSubstitutor = substitutor.putAll((PsiClass) element, args);
					PsiElement context = result.getCurrentFileResolveContext();
					GroovyResolveResultImpl newResult = new GroovyResolveResultImpl(element, context, null,
							newSubstitutor, result.isAccessible(), result.isStaticsOK());
					results[i] = newResult;
					if(context instanceof GrImportStatement)
					{
						imported.add(newResult);
					}
				}
			}
			if(!imported.isEmpty())
			{
				return imported.toArray(new GroovyResolveResult[imported.size()]);
			}

			return results;
		}

		@Nonnull
		private static GroovyResolveResult[] _resolve(@Nonnull GrCodeReferenceElementImpl ref,
				@Nonnull PsiManager manager,
				@Nonnull ReferenceKind kind)
		{
			final String refName = ref.getReferenceName();
			if(refName == null)
			{
				return GroovyResolveResult.EMPTY_ARRAY;
			}

			switch(kind)
			{
				case CLASS_OR_PACKAGE_FQ:
				case CLASS_FQ:
				case PACKAGE_FQ:
					String qName = PsiUtil.getQualifiedReferenceText(ref);
					LOG.assertTrue(qName != null, ref.getText());

					JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
					if(kind == ReferenceKind.CLASS_OR_PACKAGE_FQ || kind == ReferenceKind.CLASS_FQ)
					{
						final PsiFile file = ref.getContainingFile();
						if(qName.indexOf('.') > 0 || file instanceof GroovyFile && ((GroovyFile) file).getPackageName
								().isEmpty())
						{
							PsiClass aClass = facade.findClass(qName, ref.getResolveScope());
							if(aClass != null)
							{
								boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
								return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
							}
						}
					}

					if(kind == ReferenceKind.CLASS_OR_PACKAGE_FQ || kind == ReferenceKind.PACKAGE_FQ)
					{
						PsiPackage aPackage = facade.findPackage(qName);
						if(aPackage != null)
						{
							return new GroovyResolveResult[]{new GroovyResolveResultImpl(aPackage, true)};
						}
					}

					break;

				case CLASS:
				{
					EnumSet<ClassHint.ResolveKind> kinds = kind == ReferenceKind.CLASS ? ClassHint.RESOLVE_KINDS_CLASS
							: ClassHint.RESOLVE_KINDS_CLASS_PACKAGE;
					ResolverProcessorImpl processor = new ClassResolverProcessor(refName, ref, kinds);
					GrCodeReferenceElement qualifier = ref.getQualifier();
					if(qualifier != null)
					{
						PsiElement qualifierResolved = qualifier.resolve();
						if(qualifierResolved instanceof PsiPackage || qualifierResolved instanceof PsiClass)
						{
							qualifierResolved.processDeclarations(processor, ResolveState.initial(), null, ref);
							return processor.getCandidates();
						}
					}
					else
					{
						// if ref is an annotation name reference we should not process declarations of annotated
						// elements
						// because inner annotations are not permitted and it can cause infinite recursion
						PsiElement placeToStartWalking = isAnnotationRef(ref) ? getContainingFileSkippingStubFiles
								(ref) : ref;
						if(placeToStartWalking != null)
						{
							ResolveUtil.treeWalkUp(placeToStartWalking, processor, false);
							GroovyResolveResult[] candidates = processor.getCandidates();
							if(candidates.length > 0)
							{
								return candidates;
							}
						}

						if(kind == ReferenceKind.CLASS_OR_PACKAGE)
						{
							PsiPackage pkg = JavaPsiFacade.getInstance(ref.getProject()).findPackage(refName);
							if(pkg != null)
							{
								return new GroovyResolveResult[]{new GroovyResolveResultImpl(pkg, true)};
							}
						}
					}

					break;
				}

				case CLASS_OR_PACKAGE:
				{
					GroovyResolveResult[] classResult = _resolve(ref, manager, ReferenceKind.CLASS);

					if(classResult.length == 1 && !classResult[0].isAccessible())
					{
						GroovyResolveResult[] packageResult = _resolve(ref, manager, ReferenceKind.PACKAGE_FQ);
						if(packageResult.length != 0)
						{
							return packageResult;
						}
					}
					else if(classResult.length == 0)
					{
						return _resolve(ref, manager, ReferenceKind.PACKAGE_FQ);
					}

					return classResult;
				}
				case STATIC_MEMBER_FQ:
				{
					final GrCodeReferenceElement qualifier = ref.getQualifier();
					if(qualifier != null)
					{
						final PsiElement resolved = qualifier.resolve();
						if(resolved instanceof PsiClass)
						{
							final PsiClass clazz = (PsiClass) resolved;
							PsiResolveHelper helper = JavaPsiFacade.getInstance(clazz.getProject()).getResolveHelper();
							List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();

							processFields(ref, refName, clazz, helper, result);
							processMethods(ref, refName, clazz, helper, result);
							processInnerClasses(ref, refName, clazz, helper, result);

							if(result.isEmpty())
							{
								processAccessors(ref, refName, clazz, helper, result);
							}

							return result.toArray(new GroovyResolveResult[result.size()]);
						}
					}
					break;
				}
				case CLASS_IN_QUALIFIED_NEW:
				{
					if(ref.getParent() instanceof GrCodeReferenceElement)
					{
						return GroovyResolveResult.EMPTY_ARRAY;
					}
					final GrNewExpression newExpression = PsiTreeUtil.getParentOfType(ref, GrNewExpression.class);
					assert newExpression != null;
					final GrExpression qualifier = newExpression.getQualifier();
					assert qualifier != null;

					final PsiType type = qualifier.getType();
					if(!(type instanceof PsiClassType))
					{
						break;
					}

					final PsiClassType classType = (PsiClassType) type;
					final PsiClass psiClass = classType.resolve();
					if(psiClass == null)
					{
						break;
					}

					final PsiClass[] allInnerClasses = psiClass.getAllInnerClasses();
					ArrayList<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
					PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getProject()).getResolveHelper();

					for(final PsiClass innerClass : allInnerClasses)
					{
						if(refName.equals(innerClass.getName()))
						{
							result.add(new GroovyResolveResultImpl(innerClass, helper.isAccessible(innerClass, ref,
									null)));
						}
					}
					return result.toArray(new GroovyResolveResult[result.size()]);
				}
			}

			return GroovyResolveResult.EMPTY_ARRAY;
		}

		private static PsiFile getContainingFileSkippingStubFiles(GrCodeReferenceElementImpl ref)
		{
			PsiFile file = ref.getContainingFile();
			while(file != null && !file.isPhysical() && file.getContext() != null)
			{
				PsiElement context = file.getContext();
				file = context.getContainingFile();
			}
			return file;
		}

		private static boolean isAnnotationRef(GrCodeReferenceElement ref)
		{
			final PsiElement parent = ref.getParent();
			return parent instanceof GrAnnotation || parent instanceof GrCodeReferenceElement && isAnnotationRef(
					(GrCodeReferenceElement) parent);
		}

		private static void processAccessors(GrCodeReferenceElementImpl ref,
				String refName,
				PsiClass clazz,
				PsiResolveHelper helper,
				List<GroovyResolveResult> result)
		{
			final String booleanGetter = GroovyPropertyUtils.getGetterNameBoolean(refName);
			final String nonBooleanGetter = GroovyPropertyUtils.getGetterNameNonBoolean(refName);
			final String setter = GroovyPropertyUtils.getSetterName(refName);

			processMethods(ref, booleanGetter, clazz, helper, result);
			if(!result.isEmpty())
			{
				return;
			}
			processMethods(ref, nonBooleanGetter, clazz, helper, result);
			if(!result.isEmpty())
			{
				return;
			}
			processMethods(ref, setter, clazz, helper, result);
		}

		private static void processInnerClasses(GrCodeReferenceElementImpl ref,
				String refName,
				PsiClass clazz,
				PsiResolveHelper helper,
				List<GroovyResolveResult> result)
		{
			final PsiClass innerClass = clazz.findInnerClassByName(refName, true);
			if(innerClass != null && innerClass.hasModifierProperty(PsiModifier.STATIC))
			{
				result.add(new GroovyResolveResultImpl(innerClass, helper.isAccessible(innerClass, ref, null)));
			}
		}

		private static void processFields(GrCodeReferenceElementImpl ref,
				String refName,
				PsiClass clazz,
				PsiResolveHelper helper,
				List<GroovyResolveResult> result)
		{
			final PsiField field = clazz.findFieldByName(refName, true);
			if(field != null && field.hasModifierProperty(PsiModifier.STATIC))
			{
				result.add(new GroovyResolveResultImpl(field, helper.isAccessible(field, ref, null)));
			}
		}

		private static void processMethods(GrCodeReferenceElementImpl ref,
				String refName,
				PsiClass clazz,
				PsiResolveHelper helper,
				List<GroovyResolveResult> result)
		{
			final PsiMethod[] methods = clazz.findMethodsByName(refName, true);
			for(PsiMethod method : methods)
			{
				result.add(new GroovyResolveResultImpl(method, helper.isAccessible(method, ref, null)));
			}
		}
	}

	private static final OurResolver RESOLVER = new OurResolver();

	@Override
	public GroovyResolveResult advancedResolve()
	{
		ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, false, RESOLVER);
		return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
	}

	@Override
	@Nonnull
	public GroovyResolveResult[] multiResolve(boolean incompleteCode)
	{
		final ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode,
				RESOLVER);
		if(results.length == 0)
		{
			return GroovyResolveResult.EMPTY_ARRAY;
		}

		return (GroovyResolveResult[]) results;
	}

	@Nonnull
	@Override
	public PsiType[] getTypeArguments()
	{
		GrTypeArgumentList typeArgumentList = getTypeArgumentList();
		if(typeArgumentList != null && typeArgumentList.isDiamond())
		{
			return inferDiamondTypeArguments();
		}
		else
		{
			return super.getTypeArguments();
		}
	}

	private PsiType[] inferDiamondTypeArguments()
	{
		PsiElement parent = getParent();
		if(!(parent instanceof GrNewExpression))
		{
			return PsiType.EMPTY_ARRAY;
		}

		PsiType lType = PsiImplUtil.inferExpectedTypeForDiamond((GrNewExpression) parent);

		if(lType instanceof PsiClassType)
		{
			return ((PsiClassType) lType).getParameters();
		}

		return PsiType.EMPTY_ARRAY;
	}
}
