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
package org.jetbrains.plugins.groovy.impl.refactoring;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author ilyas
 */
@Deprecated
@DeprecationInfo("Use GroovyRefactoringLocalize")
@MigratedExtensionsTo(GroovyRefactoringLocalize.class)
public class GroovyRefactoringBundle extends AbstractBundle
{
	private static final GroovyRefactoringBundle ourInstance = new GroovyRefactoringBundle();

	private GroovyRefactoringBundle()
	{
		super("messages.GroovyRefactoringBundle");
	}

	public static String message(@PropertyKey(resourceBundle = "messages.GroovyRefactoringBundle") String key)
	{
		return ourInstance.getMessage(key);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.GroovyRefactoringBundle") String key, Object... params)
	{
		return ourInstance.getMessage(key, params);
	}
}