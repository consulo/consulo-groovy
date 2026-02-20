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
package org.jetbrains.plugins.groovy.impl.intentions.other;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrCreateMissingSwitchBranchesIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.grCreateMissingSwitchBranchesIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        if (!(element instanceof GrSwitchStatement)) {
            return;
        }

        List<PsiEnumConstant> constants = findUnusedConstants((GrSwitchStatement) element);
        if (constants.isEmpty()) {
            return;
        }

        PsiEnumConstant first = constants.get(0);
        PsiClass aClass = first.getContainingClass();
        if (aClass == null) {
            return;
        }
        String qName = aClass.getQualifiedName();

        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
        PsiElement anchor = findAnchor(element);
        for (PsiEnumConstant constant : constants) {
            GrCaseSection section = factory.createSwitchSection("case " + qName + "." + constant.getName() + ":\n break");
            PsiElement added = element.addBefore(section, anchor);

            element.addBefore(factory.createLineTerminator(1), anchor);

            JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);
        }
    }

    @Nullable
    private static PsiElement findAnchor(PsiElement element) {
        PsiElement last = element.getLastChild();
        if (last != null && last.getNode().getElementType() == GroovyTokenTypes.mRCURLY) {
            return last;
        }
        return null;
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {
            @Override
            public boolean satisfiedBy(PsiElement element) {
                if (!(element instanceof GrSwitchStatement)) {
                    return false;
                }

                List<PsiEnumConstant> unused = findUnusedConstants((GrSwitchStatement) element);
                return !unused.isEmpty();
            }
        };
    }

    private static List<PsiEnumConstant> findUnusedConstants(GrSwitchStatement switchStatement) {
        GrExpression condition = switchStatement.getCondition();
        if (condition == null) {
            return Collections.emptyList();
        }

        PsiType type = condition.getType();
        if (!(type instanceof PsiClassType)) {
            return Collections.emptyList();
        }

        PsiClass resolved = ((PsiClassType) type).resolve();
        if (resolved == null || !resolved.isEnum()) {
            return Collections.emptyList();
        }

        PsiField[] fields = resolved.getFields();
        List<PsiEnumConstant> constants = ContainerUtil.findAll(fields, PsiEnumConstant.class);

        GrCaseSection[] sections = switchStatement.getCaseSections();
        for (GrCaseSection section : sections) {
            for (GrCaseLabel label : section.getCaseLabels()) {
                GrExpression value = label.getValue();
                if (value instanceof GrReferenceExpression) {
                    PsiElement r = ((GrReferenceExpression) value).resolve();
                    constants.remove(r);
                }
            }
        }
        return constants;
    }
}
