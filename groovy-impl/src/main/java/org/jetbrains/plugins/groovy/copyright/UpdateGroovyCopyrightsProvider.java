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

/*
 * User: anna
 * Date: 30-Nov-2009
 */
package org.jetbrains.plugins.groovy.copyright;

import java.util.List;

import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import com.maddyhome.idea.copyright.psi.UpdateJavaFileCopyright;   import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright;
import com.maddyhome.idea.copyright.ui.TemplateCommentPanel;
import consulo.copyright.config.CopyrightFileConfig;

public class UpdateGroovyCopyrightsProvider extends UpdateCopyrightsProvider<CopyrightFileConfig>
{
	@NotNull
	@Override
	public UpdatePsiFileCopyright<CopyrightFileConfig> createInstance(@NotNull PsiFile file, @NotNull CopyrightProfile copyrightProfile)
	{
		return new UpdateJavaFileCopyright(file, copyrightProfile)
		{
			@Override
			protected boolean accept()
			{
				return getFile() instanceof GroovyFile;
			}

			@Override
			protected PsiElement[] getImportsList()
			{
				return ((GroovyFile) getFile()).getImportStatements();
			}

			@Override
			protected PsiElement getPackageStatement()
			{
				return ((GroovyFile) getFile()).getPackageDefinition();
			}

			@Override
			protected void checkCommentsForTopClass(PsiClass topclass, int location, List<PsiComment> comments)
			{
				if(!(topclass instanceof GroovyScriptClass))
				{
					super.checkCommentsForTopClass(topclass, location, comments);
					return;
				}
				final GroovyFile containingFile = (GroovyFile) topclass.getContainingFile();

				PsiElement last = containingFile.getFirstChild();
				while(last != null && !(last instanceof GrStatement))
				{
					last = last.getNextSibling();
				}
				checkComments(last, location == LOCATION_BEFORE_CLASS, comments);
			}
		};
	}

	@NotNull
	@Override
	public CopyrightFileConfig createDefaultOptions()
	{
		return new CopyrightFileConfig();
	}

	@NotNull
	@Override
	public TemplateCommentPanel createConfigurable(@NotNull Project project, @NotNull TemplateCommentPanel parentPane, @NotNull FileType fileType)
	{
		return new TemplateCommentPanel(fileType, parentPane, project)
		{
			@Override
			public void addAdditionalComponents(@NotNull JPanel additionalPanel)
			{
				addLocationInFile(new String[]{
						"Before Package",
						"Before Imports",
						"Before Class"
				});
			}
		};
	}
}