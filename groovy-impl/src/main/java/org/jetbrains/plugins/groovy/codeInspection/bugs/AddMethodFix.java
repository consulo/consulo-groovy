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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Properties;

public class AddMethodFix extends GroovyFix
{
	private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.codeInspection.bugs" +
			".GroovyRangeTypeCheckInspection");
	private final String myMethodName;
	private final GrTypeDefinition myClass;

	public AddMethodFix(String methodName, GrTypeDefinition aClass)
	{
		myMethodName = methodName;
		myClass = aClass;
	}

	@Override
	protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
	{

		if(myClass.isInterface())
		{
			final GrMethod method = GroovyPsiElementFactory.getInstance(project).createMethodFromText("def " + myClass
					.getName() + " " + myMethodName + "();");
			myClass.add(method);
		}
		else
		{
			String templName = JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY;
			final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);

			Properties properties = new Properties();

			String returnType = generateTypeText(myClass);
			properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType);
			properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType
					(JavaPsiFacade.getElementFactory(project).createType(myClass)));
			properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "");
			properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, myClass.getQualifiedName());
			properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, myClass.getName());
			properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, myMethodName);

			try
			{
				String bodyText = StringUtil.replace(template.getText(properties), ";", "");
				final GrCodeBlock newBody = GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n"
						+ bodyText + "\n");

				final GrMethod method = GroovyPsiElementFactory.getInstance(project).createMethodFromText("",
						myMethodName, returnType, ArrayUtil.EMPTY_STRING_ARRAY, myClass);
				method.setBlock(newBody);
				myClass.add(method);
			}
			catch(IOException e)
			{
				LOG.error(e);
			}
		}
	}


	@Nonnull
	@Override
	public String getName()
	{
		return GroovyInspectionBundle.message("add.method", myMethodName, myClass.getName());
	}

	static String generateTypeText(GrTypeDefinition aClass)
	{
		StringBuilder returnType = new StringBuilder(aClass.getName());
		final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
		if(typeParameters.length > 0)
		{
			returnType.append('<');
			for(PsiTypeParameter typeParameter : typeParameters)
			{
				returnType.append(typeParameter.getName()).append(", ");
			}
			returnType.replace(returnType.length() - 2, returnType.length(), ">");
		}
		return returnType.toString();
	}
}
