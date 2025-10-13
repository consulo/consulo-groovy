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
package org.jetbrains.plugins.groovy.impl.codeInspection.type;

import consulo.codeEditor.Editor;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.annotation.Annotation;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleExpression;

import java.util.List;

public class GroovyStaticTypeCheckVisitor extends GroovyTypeCheckVisitor {

    private AnnotationHolder myHolder;

    public void setAnnotationHolder(@Nonnull AnnotationHolder annotationHolder) {
        myHolder = annotationHolder;
    }

    @Override
    protected void processTupleAssignment(@Nonnull GrTupleExpression tupleExpression, @Nonnull GrExpression initializer) {
        if (initializer instanceof GrListOrMap && !((GrListOrMap) initializer).isMap()) {
            final GrListOrMap initializerList = (GrListOrMap) initializer;
            final GrExpression[] vars = tupleExpression.getExpressions();
            final GrExpression[] expressions = initializerList.getInitializers();
            if (vars.length > expressions.length) {
                registerError(
                    initializer,
                    GroovyLocalize.incorrectNumberOfValues(vars.length, expressions.length),
                    LocalQuickFix.EMPTY_ARRAY,
                    ProblemHighlightType.GENERIC_ERROR
                );
            }
            else {
                for (int i = 0; i < vars.length; i++) {
                    processAssignmentWithinMultipleAssignment(vars[i], expressions[i], tupleExpression);
                }
            }
        }
        else {
            registerError(
                initializer,
                GroovyLocalize.multipleAssignmentsWithoutListExpr(),
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.GENERIC_ERROR
            );
        }
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
        super.visitAssignmentExpression(assignment);
    }

    @Override
    protected void registerError(
        @Nonnull final PsiElement location,
        @Nonnull final LocalizeValue description,
        @Nullable final LocalQuickFix[] fixes,
        final ProblemHighlightType highlightType
    ) {
        if (highlightType != ProblemHighlightType.GENERIC_ERROR) {
            return;
        }
        final List<IntentionAction> intentions = ContainerUtil.newArrayList();
        if (fixes != null) {
            for (final LocalQuickFix fix : fixes) {
                intentions.add(new IntentionAction() {
                    @Nonnull
                    @Override
                    public LocalizeValue getText() {
                        return fix.getName();
                    }

                    @Override
                    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
                        return true;
                    }

                    @Override
                    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
                        final InspectionManager manager = InspectionManager.getInstance(project);
                        final ProblemDescriptor descriptor =
                            manager.createProblemDescriptor(location, description.get(), fixes, highlightType, fixes.length == 1, false);
                        fix.applyFix(project, descriptor);
                    }

                    @Override
                    public boolean startInWriteAction() {
                        return true;
                    }
                });
            }
        }
        registerError(location, description, intentions.toArray(new IntentionAction[intentions.size()]), highlightType);
    }

    protected void registerError(
        @Nonnull final PsiElement location,
        @Nonnull final String description,
        @Nullable final IntentionAction[] fixes,
        final ProblemHighlightType highlightType
    ) {
        if (highlightType != ProblemHighlightType.GENERIC_ERROR) {
            return;
        }
        final Annotation annotation = myHolder.createErrorAnnotation(location, description);
        if (fixes == null) {
            return;
        }
        for (IntentionAction intention : fixes) {
            annotation.registerFix(intention);
        }
    }

    @Override
    public void visitElement(GroovyPsiElement element) {
        // do nothing & disable recursion
    }
}
