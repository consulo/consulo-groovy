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

package org.jetbrains.plugins.groovy;

import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.file.FileTypeManager;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Represents Groovy file properites, such as extension etc.
 *
 * @author ilyas
 */
public class GroovyFileType extends LanguageFileType
{
	public static final GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
	@Deprecated
	public static final Language GROOVY_LANGUAGE = GroovyLanguage.INSTANCE;
	public static final String DEFAULT_EXTENSION = "groovy";

	private GroovyFileType()
	{
		super(GroovyLanguage.INSTANCE);
	}

	@Nonnull
	public String getId()
	{
		return "Groovy";
	}

	@Nonnull
	public LocalizeValue getDescription()
	{
		return LocalizeValue.localizeTODO("Groovy Files");
	}

	@Nonnull
	public String getDefaultExtension()
	{
		return DEFAULT_EXTENSION;
	}

	@Nonnull
	public Image getIcon()
	{
		return JetgroovyIcons.Groovy.Groovy_16x16;
	}

	@Nonnull
	public static FileType[] getGroovyEnabledFileTypes()
	{
		Collection<FileType> result = new LinkedHashSet<>();
		result.addAll(ContainerUtil.filter(
				FileTypeManager.getInstance().getRegisteredFileTypes(),
				GroovyFileType::isGroovyEnabledFileType
		));
		return result.toArray(FileType.EMPTY_ARRAY);
	}

	private static boolean isGroovyEnabledFileType(FileType ft)
	{
		return ft instanceof GroovyEnabledFileType ||
				ft instanceof LanguageFileType && ((LanguageFileType) ft).getLanguage() == GroovyLanguage.INSTANCE;
	}
}
