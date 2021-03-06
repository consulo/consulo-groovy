/*
 * Copyright 2013 Consulo.org
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
package consulo.groovy;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.IconDescriptor;
import consulo.ide.IconDescriptorUpdater;
import consulo.ide.IconDescriptorUpdaters;
import icons.JetgroovyIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 22:25/19.07.13
 */
public class GroovyIconDescriptorUpdater implements IconDescriptorUpdater
{
	@RequiredReadAction
	@Override
	public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags)
	{
		if(element instanceof GroovyFile)
		{
			GroovyFile file = (GroovyFile) element;
			final GrTypeDefinition[] typeDefinitions = file.getTypeDefinitions();
			if(typeDefinitions.length == 1)
			{
				IconDescriptorUpdaters.processExistingDescriptor(iconDescriptor, typeDefinitions[0], flags);
			}
			else
			{
				iconDescriptor.setMainIcon(GroovyScriptTypeDetector.getIcon(file));
			}
		}
		else if(element instanceof GrTypeDefinition)
		{
			final GrTypeDefinition psiClass = (GrTypeDefinition) element;
			if(psiClass.isEnum())
			{
				iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.Enum);
			}
			else if(psiClass.isAnnotationType())
			{
				iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.AnnotationType);
			}
			else if(psiClass.isInterface())
			{
				iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.Interface);
			}
			else if(psiClass.isTrait())
			{
				iconDescriptor.setMainIcon(JetgroovyIcons.Groovy.Trait);
			}
			else
			{
				final boolean abst = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
				iconDescriptor.setMainIcon(abst ? JetgroovyIcons.Groovy.AbstractClass : JetgroovyIcons.Groovy.Class);
			}

			// if(!DumbService.getInstance(element.getProject()).isDumb()) {
		/*if (GroovyRunnerUtil.isRunnable(psiClass)) {
          iconDescriptor.addLayerIcon(AllIcons.Nodes.RunnableMark);
        }*/
			// }
		}
	}
}
