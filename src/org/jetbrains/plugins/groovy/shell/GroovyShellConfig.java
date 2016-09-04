/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.shell;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import consulo.java.module.extension.JavaModuleExtension;

public abstract class GroovyShellConfig
{

	@NotNull
	public abstract String getWorkingDirectory(@NotNull Module module);

	@NotNull
	public abstract JavaParameters createJavaParameters(@NotNull Module module) throws ExecutionException;


	public abstract boolean canRun(@NotNull Module module);

	@NotNull
	public abstract String getVersion(@NotNull Module module);

	@Nullable
	public PsiElement getContext(@NotNull Module module)
	{
		return null;
	}

	public boolean isSuitableModule(Module module)
	{
		return ModuleUtilCore.getExtension(module, JavaModuleExtension.class) != null;
	}

	public abstract Collection<Module> getPossiblySuitableModules(Project project);

	public abstract String getTitle();
}