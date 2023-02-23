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

package org.jetbrains.plugins.groovy.refactoring;

import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.editor.refactoring.RefactoringHelper;
import consulo.usage.UsageInfo;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GroovyImportOptimizerRefactoringHelper implements RefactoringHelper<Set<GroovyFile>>
{
	@Override
	public Set<GroovyFile> prepareOperation(UsageInfo[] usages)
	{
		Set<GroovyFile> files = new HashSet<GroovyFile>();
		for(UsageInfo usage : usages)
		{
			if(usage.isNonCodeUsage)
			{
				continue;
			}
			PsiFile file = usage.getFile();
			if(file instanceof GroovyFile && file.isValid() && file.isPhysical())
			{
				files.add((GroovyFile) file);
			}
		}
		return files;
	}

	@Override
	public void performOperation(final Project project, final Set<GroovyFile> files)
	{
		final ProgressManager progressManager = ProgressManager.getInstance();
		final Map<GroovyFile, Pair<List<GrImportStatement>, Set<GrImportStatement>>> redundants = new
				HashMap<GroovyFile, Pair<List<GrImportStatement>, Set<GrImportStatement>>>();
		final Runnable findUnusedImports = new Runnable()
		{
			@Override
			public void run()
			{
				final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
				final int total = files.size();
				int i = 0;
				for(final GroovyFile file : files)
				{
					if(!file.isValid())
					{
						continue;
					}
					final VirtualFile virtualFile = file.getVirtualFile();
					if(!ProjectRootManager.getInstance(project).getFileIndex().isInSource(virtualFile))
					{
						continue;
					}
					if(progressIndicator != null)
					{
						progressIndicator.setText2(virtualFile.getPresentableUrl());
						progressIndicator.setFraction((double) i++ / total);
					}
					ApplicationManager.getApplication().runReadAction(new Runnable()
					{
						@Override
						public void run()
						{
							final Set<GrImportStatement> usedImports = GroovyImportUtil.findUsedImports(file);
							final List<GrImportStatement> validImports = PsiUtil.getValidImportStatements(file);
							redundants.put(file, Pair.create(validImports, usedImports));
						}
					});
				}
			}
		};

		if(!progressManager.runProcessWithProgressSynchronously(findUnusedImports, "Optimizing imports (Groovy) ... ",
				false, project))
		{
			return;
		}

		WriteAction.run(() ->
		{
			for(GroovyFile groovyFile : redundants.keySet())
			{
				if(!groovyFile.isValid())
				{
					continue;
				}
				final Pair<List<GrImportStatement>, Set<GrImportStatement>> pair = redundants.get(groovyFile);
				final List<GrImportStatement> validImports = pair.getFirst();
				final Set<GrImportStatement> usedImports = pair.getSecond();
				for(GrImportStatement importStatement : validImports)
				{
					if(!usedImports.contains(importStatement))
					{
						groovyFile.removeImport(importStatement);
					}
				}
			}
		});
	}

}
