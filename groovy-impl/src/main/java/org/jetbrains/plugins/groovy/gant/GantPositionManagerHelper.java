/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.internal.com.sun.jdi.ReferenceType;

/**
 * @author ilyas
 */
public class GantPositionManagerHelper extends ScriptPositionManagerHelper
{
	@Override
	public boolean isAppropriateRuntimeName(@Nonnull final String runtimeName)
	{
		return true;
	}

	@Override
	public boolean isAppropriateScriptFile(@Nonnull final GroovyFile scriptFile)
	{
		return GroovyScriptUtil.isSpecificScriptFile(scriptFile, GantScriptType.INSTANCE);
	}

	@Override
	public PsiFile getExtraScriptIfNotFound(@Nonnull ReferenceType refType, @Nonnull final String runtimeName, @Nonnull final Project project, @Nonnull GlobalSearchScope scope)
	{
		try
		{
			final String fileName = StringUtil.getShortName(runtimeName);
			PsiFile[] files = FilenameIndex.getFilesByName(project, fileName + "." + GantScriptType.DEFAULT_EXTENSION, scope);
			if(files.length == 0)
			{
				files = FilenameIndex.getFilesByName(project, fileName + "." + GantScriptType.DEFAULT_EXTENSION, GlobalSearchScope.allScope(project));
			}
			if(files.length == 1)
			{
				return files[0];
			}

			if(files.length == 0)
			{
				files = FilenameIndex.getFilesByName(project, fileName + ".groovy", scope);
				if(files.length == 0)
				{
					files = FilenameIndex.getFilesByName(project, fileName + "." + GantScriptType.DEFAULT_EXTENSION, GlobalSearchScope.allScope(project));
				}

				PsiFile candidate = null;
				for(PsiFile file : files)
				{
					if(GroovyScriptUtil.isSpecificScriptFile(file, GantScriptType.INSTANCE))
					{
						if(candidate != null)
						{
							return null;
						}
						candidate = file;
					}
				}

				return candidate;
			}
		}
		catch(ProcessCanceledException | IndexNotReadyException ignored)
		{
		}
		return null;
	}
}
