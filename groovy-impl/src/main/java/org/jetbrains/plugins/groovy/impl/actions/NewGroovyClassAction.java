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

package org.jetbrains.plugins.groovy.impl.actions;

import com.intellij.java.impl.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.java.language.psi.JavaDirectoryService;
import consulo.application.dumb.DumbAware;
import consulo.dataContext.DataContext;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.groovy.localize.GroovyLocalize;
import consulo.ide.IdeView;
import consulo.ide.action.CreateFileFromTemplateDialog;
import consulo.ide.impl.idea.ide.actions.WeighingActionGroup;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.LangDataKeys;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

public class NewGroovyClassAction extends JavaCreateTemplateInPackageAction<GrTypeDefinition> implements DumbAware {

    public NewGroovyClassAction() {
        super(GroovyLocalize.newclassMenuActionText(), GroovyLocalize.newclassMenuActionDescription(), JetgroovyIcons.Groovy.Class, true);
    }

    @Override
    protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
        builder.setTitle(GroovyLocalize.newclassDlgTitle())
            .addKind(LocalizeValue.localizeTODO("Class"), JetgroovyIcons.Groovy.Class,
                GroovyTemplates.GROOVY_CLASS)
            .addKind(LocalizeValue.localizeTODO("Interface"), JetgroovyIcons.Groovy.Interface,
                GroovyTemplates.GROOVY_INTERFACE);

        if (GroovyConfigUtils.getInstance().isVersionAtLeast(directory, GroovyConfigUtils.GROOVY2_3, true)) {
            builder.addKind(LocalizeValue.localizeTODO("Trait"), JetgroovyIcons.Groovy.Trait, GroovyTemplates.GROOVY_TRAIT);
        }

        builder.addKind(LocalizeValue.localizeTODO("Enum"), JetgroovyIcons.Groovy.Enum, GroovyTemplates.GROOVY_ENUM)
            .addKind(LocalizeValue.of("Annotation"), JetgroovyIcons.Groovy.AnnotationType, GroovyTemplates.GROOVY_ANNOTATION);

        for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
            FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
            if (fileType.equals(GroovyFileType.GROOVY_FILE_TYPE) && JavaDirectoryService.getInstance().getPackage(directory) != null) {
                builder.addKind(LocalizeValue.of(template.getName()), JetgroovyIcons.Groovy.Class, template.getName());
            }
        }
    }

    @Override
    protected boolean isAvailable(DataContext dataContext) {
        return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(dataContext.getData(LangDataKeys.MODULE));
    }

    @Override
    protected LocalizeValue getActionName(PsiDirectory directory, String newName, String templateName) {
        return GroovyLocalize.newclassMenuActionText();
    }

    @Override
    protected PsiElement getNavigationElement(@Nonnull GrTypeDefinition createdElement) {
        return createdElement.getLBrace();
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        if (!presentation.isVisible()) {
            return;
        }

        IdeView view = e.getData(IdeView.KEY);
        if (view == null) {
            return;
        }
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }

        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        for (PsiDirectory dir : view.getDirectories()) {
            if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && checkPackageExists(dir)) {
                for (GroovySourceFolderDetector detector : GroovySourceFolderDetector.EP_NAME.getExtensionList()) {
                    if (detector.isGroovySourceFolder(dir)) {
                        presentation.putClientProperty(WeighingActionGroup.WEIGHT_KEY, WeighingActionGroup.HIGHER_WEIGHT);
                        break;
                    }
                }
                return;
            }
        }
    }

    @Override
    protected final GrTypeDefinition doCreate(PsiDirectory dir,
                                              String className,
                                              String templateName) throws IncorrectOperationException {
        final String fileName = className + NewGroovyActionBase.GROOVY_EXTENSION;
        final PsiFile fromTemplate = GroovyTemplatesFactory.createFromTemplate(dir, className, fileName, templateName,
            true);
        if (fromTemplate instanceof GroovyFile) {
            CodeStyleManager.getInstance(fromTemplate.getManager()).reformat(fromTemplate);
            return ((GroovyFile) fromTemplate).getTypeDefinitions()[0];
        }
        final LocalizeValue description = fromTemplate.getFileType().getDescription();
        throw new IncorrectOperationException(GroovyLocalize.groovyFileExtensionIsNotMappedToGroovyFileType(description).get());
    }
}
