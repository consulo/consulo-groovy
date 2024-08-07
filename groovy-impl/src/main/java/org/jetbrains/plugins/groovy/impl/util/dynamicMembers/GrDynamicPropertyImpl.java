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
package org.jetbrains.plugins.groovy.impl.util.dynamicMembers;

import com.intellij.java.language.psi.*;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GrDynamicPropertyImpl extends LightElement implements GrField
{
	private final GrField myField;
	private final PsiClass myContainingClass;
	private final PsiElement myNavigationalElement;

	public GrDynamicPropertyImpl(PsiClass containingClass, GrField field, PsiElement navigationalElement)
	{
		super(field.getManager(), field.getLanguage());
		myContainingClass = containingClass;
		if(navigationalElement != null)
		{
			myNavigationalElement = navigationalElement;
		}
		else
		{
			myNavigationalElement = field;
		}

		myField = field;
	}

	@Override
	public GrDocComment getDocComment()
	{
		return null;
	}

	@Override
	public PsiClass getContainingClass()
	{
		return myContainingClass;
	}

	@Override
	public boolean isDeprecated()
	{
		return false;
	}

	@Nonnull
	@Override
	public PsiElement getNavigationElement()
	{
		return myNavigationalElement;
	}

	@Override
	public PsiFile getContainingFile()
	{
		return myContainingClass != null ? myContainingClass.getContainingFile() : null;
	}


	@Override
	public String toString()
	{
		return "Dynamic Property: " + getName();
	}

	@Override
	@Nonnull
	public String getName()
	{
		return myField.getName();
	}

	@Override
	@Nonnull
	public PsiType getType()
	{
		return myField.getType();
	}

	@Override
	public GrModifierList getModifierList()
	{
		return myField.getModifierList();
	}

	@Override
	public PsiTypeElement getTypeElement()
	{
		return myField.getTypeElement();
	}

	@Override
	public boolean hasModifierProperty(@NonNls @Nonnull String name)
	{
		return myField.hasModifierProperty(name);
	}

	@Override
	public PsiExpression getInitializer()
	{
		return myField.getInitializer();
	}

	@Override
	public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException
	{
		throw new IncorrectOperationException("Cannot set initializer");
	}

	@Override
	@Nonnull
	public PsiIdentifier getNameIdentifier()
	{
		return myField.getNameIdentifier();
	}

	@Override
	public boolean hasInitializer()
	{
		return myField.hasInitializer();
	}

	@Override
	public void normalizeDeclaration() throws IncorrectOperationException
	{
		throw new IncorrectOperationException("cannot normalize declaration");
	}

	@Override
	public Object computeConstantValue()
	{
		return null;
	}

	@Override
	public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException
	{
		return this;
	}

	@Override
	public String getText()
	{
		return null;
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{

	}

	@Override
	public PsiElement copy()
	{
		return null;
	}

	@Override
	@Nonnull
	public PsiElement getNameIdentifierGroovy()
	{
		return myField.getNameIdentifierGroovy();
	}

	@Override
	public void accept(GroovyElementVisitor visitor)
	{
	}

	@Override
	public void acceptChildren(GroovyElementVisitor visitor)
	{
	}

	@Override
	public PsiType getTypeGroovy()
	{
		return myField.getTypeGroovy();
	}

	@Override
	public PsiType getDeclaredType()
	{
		return myField.getDeclaredType();
	}

	@Override
	public boolean isProperty()
	{
		return myField.isProperty();
	}

	@Override
	public GrExpression getInitializerGroovy()
	{
		return myField.getInitializerGroovy();
	}

	@Override
	public void setType(@Nullable PsiType type) throws IncorrectOperationException
	{
		throw new IncorrectOperationException("cannot set type to dynamic property");
	}

	@Override
	public GrAccessorMethod getSetter()
	{
		return myField.getSetter();
	}

	@Override
	@Nonnull
	public GrAccessorMethod[] getGetters()
	{
		return myField.getGetters();
	}

	@Override
	public GrTypeElement getTypeElementGroovy()
	{
		return myField.getTypeElementGroovy();
	}

	@Override
	@Nonnull
	public Map<String, NamedArgumentDescriptor> getNamedParameters()
	{
		return myField.getNamedParameters();
	}

	@Override
	public void setInitializerGroovy(GrExpression initializer)
	{
		throw new IncorrectOperationException("cannot set initializer to dynamic property!");
	}

	@Override
	public boolean isEquivalentTo(PsiElement another)
	{
		return another instanceof GrDynamicPropertyImpl &&
				myManager.areElementsEquivalent(myField, ((GrDynamicPropertyImpl) another).myField) &&
				myManager.areElementsEquivalent(myContainingClass, ((GrDynamicPropertyImpl) another).myContainingClass);
	}
}
