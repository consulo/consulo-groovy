/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import java.util.Collection;
import java.util.List;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import com.intellij.ide.IconDescriptorUpdaters;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author ven
 */
public class GroovyScriptClass extends LightElement implements PsiClass, SyntheticElement
{
	private final GroovyFile myFile;
	private final PsiMethod myMainMethod;
	private final PsiMethod myRunMethod;

	private final LightModifierList myModifierList;

	public GroovyScriptClass(GroovyFile file)
	{
		super(file.getManager(), file.getLanguage());
		myFile = file;
		myMainMethod = new LightMethodBuilder(getManager(), GroovyFileType.GROOVY_LANGUAGE, "main").
				setContainingClass(this).
				setMethodReturnType(PsiType.VOID).
				addParameter("args", new PsiArrayType(PsiType.getJavaLangString(getManager(), getResolveScope()))).
				addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
		myRunMethod = new LightMethodBuilder(getManager(), GroovyFileType.GROOVY_LANGUAGE, "run").
				setContainingClass(this).
				setMethodReturnType(PsiType.getJavaLangObject(getManager(), getResolveScope())).
				addModifier(PsiModifier.PUBLIC);

		myModifierList = new LightModifierList(myManager, GroovyFileType.GROOVY_LANGUAGE, PsiModifier.PUBLIC);
	}


	@Override
	public String toString()
	{
		return "Script Class:" + getQualifiedName();
	}

	@Override
	public String getText()
	{
		return "class " + getName() + " {}";
	}

	@Override
	public void accept(@NotNull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitClass(this);
		}
	}

	@Override
	public PsiElement copy()
	{
		return new GroovyScriptClass(myFile);
	}

	@Override
	public GroovyFile getContainingFile()
	{
		return myFile;
	}

	@Override
	public TextRange getTextRange()
	{
		return myFile.getTextRange();
	}

	@Override
	public int getTextOffset()
	{
		return 0;
	}

	@Override
	public boolean isValid()
	{
		return myFile.isValid() && myFile.isScript();
	}

	@Override
	@Nullable
	public String getQualifiedName()
	{
		final String name = getName();
		if(name == null)
		{
			return null;
		}

		final String packName = myFile.getPackageName();
		if(packName.length() == 0)
		{
			return name;
		}
		else
		{
			return packName + "." + name;
		}
	}

	@Override
	public boolean isInterface()
	{
		return false;
	}

	@Override
	public boolean isWritable()
	{
		return myFile.isWritable();
	}

	@Override
	public boolean isAnnotationType()
	{
		return false;
	}

	@Override
	public boolean isEnum()
	{
		return false;
	}

	@Override
	public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException
	{
		return myFile.add(element);
	}

	@Override
	public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException
	{
		return myFile.addAfter(element, anchor);
	}

	@Override
	public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException
	{
		return myFile.addBefore(element, anchor);
	}

	@Override
	public PsiReferenceList getExtendsList()
	{
		return null;
	}


	@Override
	public PsiReferenceList getImplementsList()
	{
		return null;
	}

	@Override
	@NotNull
	public PsiClassType[] getExtendsListTypes()
	{
		final PsiClassType superClassFromDSL = GroovyDslFileIndex.pocessScriptSuperClasses(myFile);
		return new PsiClassType[]{superClassFromDSL != null ? superClassFromDSL : TypesUtil.createTypeByFQClassName(GroovyCommonClassNames
				.GROOVY_LANG_SCRIPT, this)};
	}

	@Override
	@NotNull
	public PsiClassType[] getImplementsListTypes()
	{
		return PsiClassType.EMPTY_ARRAY;
	}

	@Override
	public PsiClass getSuperClass()
	{
		return PsiClassImplUtil.getSuperClass(this);
	}

	@Override
	public PsiClass[] getInterfaces()
	{
		return PsiClassImplUtil.getInterfaces(this);
	}

	@Override
	@NotNull
	public PsiClass[] getSupers()
	{
		return PsiClassImplUtil.getSupers(this);
	}

	@Override
	@NotNull
	public PsiClassType[] getSuperTypes()
	{
		return PsiClassImplUtil.getSuperTypes(this);
	}

	@Override
	public PsiClass getContainingClass()
	{
		return null;
	}

	@Override
	@NotNull
	public Collection<HierarchicalMethodSignature> getVisibleSignatures()
	{
		return PsiSuperMethodImplUtil.getVisibleSignatures(this);
	}

	@Override
	@NotNull
	public PsiField[] getFields()
	{
		return GrScriptField.getScriptFields(this);
	}

	@Override
	@NotNull
	public PsiMethod[] getMethods()
	{
		return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<PsiMethod[]>()
		{
			@Nullable
			@Override
			public Result<PsiMethod[]> compute()
			{
				PsiMethod[] methods = myFile.getMethods();

				byte hasMain = 1;
				byte hasRun = 1;
				for(PsiMethod method : methods)
				{
					if(method.isEquivalentTo(myMainMethod))
					{
						hasMain = 0;
					}
					else if(method.isEquivalentTo(myRunMethod))
					{
						hasRun = 0;
					}
				}
				if(hasMain + hasRun == 0)
				{
					return Result.create(methods, myFile);
				}

				PsiMethod[] result = new PsiMethod[methods.length + hasMain + hasRun];
				if(hasMain == 1)
				{
					result[0] = myMainMethod;
				}
				if(hasRun == 1)
				{
					result[hasMain] = myRunMethod;
				}
				System.arraycopy(methods, 0, result, hasMain + hasRun, methods.length);

				return Result.create(result, myFile);
			}
		});
	}

	@Override
	@NotNull
	public PsiMethod[] getConstructors()
	{
		return PsiMethod.EMPTY_ARRAY;
	}

	@Override
	@NotNull
	public PsiClass[] getInnerClasses()
	{
		return PsiClass.EMPTY_ARRAY;
	}

	@Override
	@NotNull
	public PsiClassInitializer[] getInitializers()
	{
		return PsiClassInitializer.EMPTY_ARRAY;
	}

	@Override
	@NotNull
	public PsiTypeParameter[] getTypeParameters()
	{
		return PsiTypeParameter.EMPTY_ARRAY;
	}

	@Override
	@NotNull
	public PsiField[] getAllFields()
	{
		return PsiClassImplUtil.getAllFields(this);
	}

	@Override
	@NotNull
	public PsiMethod[] getAllMethods()
	{
		return PsiClassImplUtil.getAllMethods(this);
	}

	@Override
	@NotNull
	public PsiClass[] getAllInnerClasses()
	{
		return PsiClassImplUtil.getAllInnerClasses(this);
	}

	@Override
	public PsiField findFieldByName(String name, boolean checkBases)
	{
		return PsiClassImplUtil.findFieldByName(this, name, checkBases);
	}

	@Override
	public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases)
	{
		return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
	}

	@Override
	@NotNull
	public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases)
	{
		return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
	}

	@Override
	@NotNull
	public PsiMethod[] findMethodsByName(String name, boolean checkBases)
	{
		return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
	}

	@Override
	@NotNull
	public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases)
	{
		return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
	}

	@Override
	@NotNull
	public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors()
	{
		return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
	}

	@Override
	public PsiClass findInnerClassByName(String name, boolean checkBases)
	{
		return PsiClassImplUtil.findInnerByName(this, name, checkBases);
	}

	@Override
	public PsiTypeParameterList getTypeParameterList()
	{
		return null;
	}

	@Override
	public boolean hasTypeParameters()
	{
		return false;
	}

	@Override
	public PsiJavaToken getLBrace()
	{
		return null;
	}

	@Override
	public PsiJavaToken getRBrace()
	{
		return null;
	}

	@Override
	public PsiIdentifier getNameIdentifier()
	{
		return null;
	}

	// very special method!
	@Override
	public PsiElement getScope()
	{
		return myFile;
	}

	@Override
	public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass)
	{
		return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
	}

	@Override
	public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep)
	{
		return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
	}

	@Override
	@Nullable
	public String getName()
	{
		String fileName = myFile.getName();
		final String name = FileUtilRt.getNameWithoutExtension(fileName);
		if(StringUtil.isJavaIdentifier(name))
		{
			return name;
		}
		else
		{
			return null;
		}
	}

	@Override
	public PsiElement setName(@NotNull String name) throws IncorrectOperationException
	{
		myFile.setName(name + "." + myFile.getViewProvider().getVirtualFile().getExtension());
		return this;
	}

	@Override
	public PsiModifierList getModifierList()
	{
		return myModifierList;
	}

	@Override
	public boolean hasModifierProperty(@NotNull String name)
	{
		return myModifierList.hasModifierProperty(name);
	}

	@Override
	public PsiDocComment getDocComment()
	{
		return null;
	}

	@Override
	public boolean isDeprecated()
	{
		return false;
	}

	@Override
	public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state,
			@Nullable PsiElement lastParent, @NotNull PsiElement place)
	{
		return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, ContainerUtil.<PsiClass>newHashSet(), lastParent, place,
				PsiUtil.getLanguageLevel(place), false);
	}

	@Override
	public PsiElement getContext()
	{
		return myFile;
	}

	//default implementations of methods from NavigationItem
	@Override
	public ItemPresentation getPresentation()
	{
		return new ItemPresentation()
		{
			@Override
			public String getPresentableText()
			{
				final String name = getName();
				return name != null ? name : "<unnamed>";
			}

			@Override
			public String getLocationString()
			{
				final String packageName = myFile.getPackageName();
				return "(groovy script" + (packageName.isEmpty() ? "" : ", " + packageName) + ")";
			}

			@Override
			public Icon getIcon(boolean open)
			{
				return IconDescriptorUpdaters.getIcon(GroovyScriptClass.this, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
			}
		};
	}

	@Override
	@Nullable
	public PsiElement getOriginalElement()
	{
		return PsiImplUtil.getOriginalElement(this, myFile);
	}

	@Override
	public void checkDelete() throws IncorrectOperationException
	{
	}

	@Override
	public void delete() throws IncorrectOperationException
	{
		myFile.delete();
	}

}

