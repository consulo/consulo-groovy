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
package org.jetbrains.plugins.groovy.extensions.debugger;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.internal.com.sun.jdi.ReferenceType;

/**
 * Class to extend debugger functionality to handle various Groovy scripts
 *
 * @author ilyas
 */
public abstract class ScriptPositionManagerHelper
{
	public static final ExtensionPointName<ScriptPositionManagerHelper> EP_NAME = ExtensionPointName.create("org.intellij.groovy.positionManagerDelegate");

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

	@javax.annotation.Nullable
	public String getOriginalScriptName(@Nonnull ReferenceType refType, @Nonnull final String runtimeName)
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
	@javax.annotation.Nullable
	public String getRuntimeScriptName(@Nonnull GroovyFile groovyFile)
	{
		return null;
	}

	/**
	 * @return Possible script to debug through in project scope if there wer not found other by standarrd methods
	 */
	@javax.annotation.Nullable
	public PsiFile getExtraScriptIfNotFound(@Nonnull ReferenceType refType, @Nonnull String runtimeName, @Nonnull Project project, @Nonnull GlobalSearchScope scope)
	{
		return null;
	}

	/**
	 * @return fully qualified runtime class name
	 * @see #getOriginalScriptName(ReferenceType, String)
	 */
	@javax.annotation.Nullable
	public String customizeClassName(@Nonnull PsiClass psiClass)
	{
		return null;
	}
}
