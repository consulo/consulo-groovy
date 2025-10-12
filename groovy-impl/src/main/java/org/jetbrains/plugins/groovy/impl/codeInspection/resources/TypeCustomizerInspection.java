/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.resources;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.resourceCompiler.ResourceCompilerConfiguration;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Set;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class TypeCustomizerInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Type customizer inspection");
    }

    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            public void visitFile(GroovyFileBase file) {
                if (!ResourceCompilerConfiguration.getInstance(file.getProject()).isResourceFile(file.getVirtualFile())) {
                    if (fileSeemsToBeTypeCustomizer(file)) {
                        final LocalQuickFix[] fixes = {new AddToResourceFix(file)};
                        final LocalizeValue message = GroovyInspectionLocalize.typeCustomizerIsNotMarkedAsAResourceFile();
                        registerError(file, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        };
    }


    private static final Set<String> CUSTOMIZER_EVENT_NAMES =
        Set.of("setup", "finish", "unresolvedVariable", "unresolvedProperty", "unresolvedAttribute", "beforeMethodCall", "afterMethodCall",
            "onMethodSelection", "methodNotFound", "beforeVisitMethod", "afterVisitMethod", "beforeVisitClass", "afterVisitClass",
            "incompatibleAssignment"
        );


    public static boolean fileSeemsToBeTypeCustomizer(@Nonnull final PsiFile file) {
        if (file instanceof GroovyFile && ((GroovyFile) file).isScript()) {
            for (GrStatement statement : ((GroovyFile) file).getStatements()) {
                if (statement instanceof GrMethodCall) {
                    GrExpression invoked = ((GrMethodCall) statement).getInvokedExpression();
                    if (invoked instanceof GrReferenceExpression &&
                        !((GrReferenceExpression) invoked).isQualified() &&
                        isCustomizerEvent(((GrReferenceExpression) invoked).getReferenceName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isCustomizerEvent(@Nullable String name) {
        return CUSTOMIZER_EVENT_NAMES.contains(name);
    }

    private static class AddToResourceFix implements LocalQuickFix {
        private final PsiFile myFile;

        public AddToResourceFix(PsiFile file) {
            myFile = file;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return GroovyInspectionLocalize.addToResources();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            final VirtualFile virtualFile = myFile.getVirtualFile();
            if (virtualFile == null) {
                return;
            }

            VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(virtualFile);
            final VirtualFile projectRoot = project.getBaseDir();
            if (sourceRoot == null) {
                final String path = VfsUtilCore.getRelativePath(virtualFile, projectRoot, '/');
                ResourceCompilerConfiguration.getInstance(project).addResourceFilePattern(path);
            }
            else {
                final String path = VfsUtilCore.getRelativePath(virtualFile, sourceRoot, '/');
                final String sourceRootPath = VfsUtilCore.getRelativePath(sourceRoot, projectRoot, '/');
                ResourceCompilerConfiguration.getInstance(project).addResourceFilePattern(sourceRootPath + ':' + path);
            }
            DaemonCodeAnalyzer.getInstance(project).restart(myFile);
        }
    }
}
