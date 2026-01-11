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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixAsIntentionAdapter;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.util.HighlightTypeUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 * @since 2014-03-21
 */
public class GrAccessibilityChecker {
    private static final Logger LOG = Logger.getInstance(GrAccessibilityChecker.class);

    private final HighlightDisplayKey myDisplayKey;
    private final boolean myInspectionEnabled;

    public GrAccessibilityChecker(@Nonnull GroovyFileBase file, @Nonnull Project project) {
        myInspectionEnabled = GroovyAccessibilityInspection.isInspectionEnabled(file, project);
        myDisplayKey = GroovyAccessibilityInspection.findDisplayKey();
    }

    @RequiredReadAction
    static GroovyFix[] buildFixes(PsiElement location, GroovyResolveResult resolveResult) {
        if (!(resolveResult.getElement() instanceof PsiMember member)
            || member instanceof PsiCompiledElement
            || member.getModifierList() == null) {
            return GroovyFix.EMPTY_ARRAY;
        }

        List<GroovyFix> fixes = new ArrayList<>();
        try {
            Project project = member.getProject();
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiModifierList modifierListCopy = facade.getElementFactory()
                .createFieldFromText("int a;", null)
                .getModifierList();
            assert modifierListCopy != null;
            modifierListCopy.setModifierProperty(PsiModifier.STATIC, member.isStatic());
            String minModifier = PsiModifier.PROTECTED;
            if (member.isProtected()) {
                minModifier = PsiModifier.PUBLIC;
            }
            String[] modifiers = {
                PsiModifier.PROTECTED,
                PsiModifier.PUBLIC,
                PsiModifier.PACKAGE_LOCAL
            };
            PsiClass accessObjectClass = PsiTreeUtil.getParentOfType(location, PsiClass.class, false);
            if (accessObjectClass == null) {
                if (!(location.getContainingFile() instanceof GroovyFile file)) {
                    return GroovyFix.EMPTY_ARRAY;
                }
                accessObjectClass = file.getScriptClass();
            }
            for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
                String modifier = modifiers[i];
                modifierListCopy.setModifierProperty(modifier, true);
                if (facade.getResolveHelper().isAccessible(member, modifierListCopy, location, accessObjectClass, null)) {
                    fixes.add(new GrModifierFix(member, modifier, true, true, GrModifierFix.MODIFIER_LIST_OWNER));
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        return fixes.toArray(new GroovyFix[fixes.size()]);
    }

    @Nullable
    @RequiredReadAction
    public HighlightInfo checkCodeReferenceElement(GrCodeReferenceElement ref) {
        return checkReferenceImpl(ref);
    }

    @RequiredReadAction
    private HighlightInfo checkReferenceImpl(GrReferenceElement ref) {
        boolean isCompileStatic = PsiUtil.isCompileStatic(ref);

        if (!needToCheck(ref, isCompileStatic)) {
            return null;
        }

        if (ref.getParent() instanceof GrConstructorCall constructorCall) {
            LocalizeValue constructorError = checkConstructorCall(constructorCall, ref);
            if (constructorError.isNotEmpty()) {
                return createAnnotationForRef(ref, isCompileStatic, constructorError);
            }
        }

        GroovyResolveResult result = ref.advancedResolve();
        LocalizeValue error = checkResolveResult(ref, result) ? GroovyLocalize.cannotAccess(ref.getReferenceName()) : LocalizeValue.empty();
        if (error.isNotEmpty()) {
            HighlightInfo info = createAnnotationForRef(ref, isCompileStatic, error);
            registerFixes(ref, result, info);
            return info;
        }

        return null;
    }

    @RequiredReadAction
    private void registerFixes(GrReferenceElement ref, GroovyResolveResult result, HighlightInfo info) {
        PsiElement element = result.getElement();
        assert element != null;
        ProblemDescriptor descriptor = InspectionManager.getInstance(ref.getProject()).createProblemDescriptor(
            element,
            element,
            "",
            HighlightTypeUtil.convertSeverityToProblemHighlight(info.getSeverity()),
            true,
            LocalQuickFix.EMPTY_ARRAY
        );
        for (GroovyFix fix : buildFixes(ref, result)) {
            QuickFixAction.registerQuickFixAction(
                info,
                new LocalQuickFixAsIntentionAdapter(fix, descriptor),
                myDisplayKey
            );
        }
    }

    @Nullable
    @RequiredReadAction
    public HighlightInfo checkReferenceExpression(GrReferenceExpression ref) {
        return checkReferenceImpl(ref);
    }

    private static boolean isStaticallyImportedProperty(GroovyResolveResult result, GrReferenceElement place) {
        if (!(place.getParent() instanceof GrImportStatement)) {
            return false;
        }

        if (!(result.getElement() instanceof PsiField field)) {
            return false;
        }

        PsiMethod getter = GroovyPropertyUtils.findGetterForField(field);
        PsiMethod setter = GroovyPropertyUtils.findSetterForField(field);

        return getter != null && PsiUtil.isAccessible(place, getter)
            || setter != null && PsiUtil.isAccessible(place, setter);
    }

    private static boolean checkResolveResult(GrReferenceElement ref, GroovyResolveResult result) {
        return result != null
            && result.getElement() != null
            && !result.isAccessible()
            && !isStaticallyImportedProperty(result, ref);
    }

    private boolean needToCheck(GrReferenceElement ref, boolean isCompileStatic) {
        return isCompileStatic || myInspectionEnabled && !GroovyAccessibilityInspection.isSuppressed(ref);
    }

    @Nonnull
    private static LocalizeValue checkConstructorCall(GrConstructorCall constructorCall, GrReferenceElement ref) {
        GroovyResolveResult result = constructorCall.advancedResolve();
        if (checkResolveResult(ref, result)) {
            return GroovyLocalize.cannotAccess(PsiFormatUtil.formatMethod(
                (PsiMethod) result.getElement(),
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME |
                    PsiFormatUtilBase.SHOW_TYPE |
                    PsiFormatUtilBase.TYPE_AFTER |
                    PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE
            ));
        }

        return LocalizeValue.empty();
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo createAnnotationForRef(
        @Nonnull GrReferenceElement ref,
        boolean strongError,
        @Nonnull LocalizeValue message
    ) {
        HighlightDisplayLevel displayLevel = strongError
            ? HighlightDisplayLevel.ERROR
            : GroovyAccessibilityInspection.getHighlightDisplayLevel(ref.getProject(), ref);
        HighlightInfo.Builder builder = GrInspectionUtil.createAnnotationForRef(ref, displayLevel, message);
        return builder == null ? null : builder.create();
    }
}
