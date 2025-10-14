/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntheticElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.GrMoveToDirFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrPackageInspection extends BaseInspection<GrPackageInspectionState> {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Package name mismatch");
    }

    @Nonnull
    @Override
    public InspectionToolState<GrPackageInspectionState> createStateProvider() {
        return new GrPackageInspectionState();
    }

    @Nullable
    protected String buildErrorString(Object... args) {
        return "Package name mismatch";
    }

    @Nonnull
    @Override
    protected BaseInspectionVisitor<GrPackageInspectionState> buildVisitor() {
        return new BaseInspectionVisitor<>() {
            @Override
            public void visitFile(GroovyFileBase file) {
                if (!(file instanceof GroovyFile)) {
                    return;
                }

                if (!myState.myCheckScripts && file.isScript()) {
                    return;
                }

                String expectedPackage = ResolveUtil.inferExpectedPackageName(file);
                String actual = file.getPackageName();
                if (!expectedPackage.equals(actual)) {

                    PsiElement toHighlight = getElementToHighlight((GroovyFile) file);
                    if (toHighlight == null) {
                        return;
                    }

                    problemsHolder.newProblem(LocalizeValue.localizeTODO("Package name mismatch. Actual: '" + actual + "', expected: '" + expectedPackage + "'"))
                        .range(toHighlight)
                        .withFixes(
                            new ChangePackageQuickFix(expectedPackage),
                            new GrMoveToDirFix(actual)
                        )
                        .create();
                }
            }
        };
    }

    @Nullable
    private static PsiElement getElementToHighlight(GroovyFile file) {
        GrPackageDefinition packageDefinition = file.getPackageDefinition();
        if (packageDefinition != null) {
            return packageDefinition;
        }

        PsiClass[] classes = file.getClasses();
        for (PsiClass aClass : classes) {
            if (!(aClass instanceof SyntheticElement) && aClass instanceof GrTypeDefinition) {
                return ((GrTypeDefinition) aClass).getNameIdentifierGroovy();
            }
        }

        GrTopStatement[] statements = file.getTopStatements();
        if (statements.length > 0) {
            GrTopStatement first = statements[0];
            if (first instanceof GrNamedElement) {
                return ((GrNamedElement) first).getNameIdentifierGroovy();
            }

            return first;
        }

        return null;
    }

    /**
     * @author Dmitry.Krasilschikov
     * @since 2007-11-01
     */
    public static class ChangePackageQuickFix implements LocalQuickFix {
        private final String myNewPackageName;

        public ChangePackageQuickFix(String newPackageName) {
            myNewPackageName = newPackageName;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return GroovyLocalize.fixPackageName();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            ((GroovyFile) file).setPackageName(myNewPackageName);
        }
    }
}
