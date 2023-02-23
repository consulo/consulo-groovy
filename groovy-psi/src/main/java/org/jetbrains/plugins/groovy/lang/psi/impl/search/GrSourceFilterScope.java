/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.search;

import javax.annotation.Nonnull;

import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.GroovyFileType;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * @author ilyas
 */
public class GrSourceFilterScope extends DelegatingGlobalSearchScope
{
	private final ProjectFileIndex myIndex;

	public GrSourceFilterScope(@Nonnull final GlobalSearchScope delegate)
	{
		super(delegate, "groovy.sourceFilter");
		myIndex = getProject() == null ? null : ProjectRootManager.getInstance(getProject()).getFileIndex();
	}

	public boolean contains(@Nonnull final VirtualFile file)
	{
		return super.contains(file) && (myIndex == null || myIndex.isInSourceContent(file)) && GroovyFileType.GROOVY_FILE_TYPE == file.getFileType();
	}
}
