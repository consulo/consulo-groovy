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
package org.jetbrains.plugins.groovy.impl.extensions.debugger;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import jakarta.annotation.Nullable;

/**
 * Class to extend debugger functionality to handle various Groovy scripts
 *
 * @author ilyas
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class ScriptPositionManagerHelper
{
	public static final ExtensionPointName<ScriptPositionManagerHelper> EP_NAME = ExtensionPointName.create(ScriptPositionManagerHelper.class);

	/**
	 * @param runtimeName runtime name of class
	 * @return true if extension may provide {@link ScriptPositionManagerHelper#getOriginalScriptName(ReferenceType, String) original}
	 * fully qualified script name or find {@link PsiFile}
	 * {@link ScriptPositionManagerHelper#getExtraScriptIfNotFound(ReferenceType, String, Project, GlobalSearchScope) corresponding}
	 * to this runtime name
	 */
	public boolean isAppropriateRuntimeName(@Nonnull String runtimeName)
	{
		return false;
	}

	@Nullable
	public String getOriginalScriptName(@Nonnull ReferenceType refType, @Nonnull String runtimeName)
	{
		return null;
	}

	/**
	 * @return true if extension may compute runtime script name given script file
	 */
	public boolean isAppropriateScriptFile(@Nonnull GroovyFile scriptFile)
	{
		return false;
	}

	/**
	 * @return Runtime script name
	 */
	@Nullable
	public String getRuntimeScriptName(@Nonnull GroovyFile groovyFile)
	{
		return null;
	}

	/**
	 * @return Possible script to debug through in project scope if there wer not found other by standarrd methods
	 */
	@Nullable
	public PsiFile getExtraScriptIfNotFound(@Nonnull ReferenceType refType, @Nonnull String runtimeName, @Nonnull Project project, @Nonnull GlobalSearchScope scope)
	{
		return null;
	}

	/**
	 * @return fully qualified runtime class name
	 * @see #getOriginalScriptName(ReferenceType, String)
	 */
	@Nullable
	public String customizeClassName(@Nonnull PsiClass psiClass)
	{
		return null;
	}
}
