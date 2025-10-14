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

package org.jetbrains.plugins.groovy.impl.codeInspection.local;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionState;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.editor.highlight.TextEditorHighlightingPass;
import consulo.language.editor.highlight.UpdateHighlightersUtil;
import consulo.language.editor.inspection.HighlightInfoTypeSeverityByKey;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ilyas
 */
public class GroovyPostHighlightingPass extends TextEditorHighlightingPass {
    private final GroovyFile myFile;
    private final Editor myEditor;
    private volatile Set<GrImportStatement> myUnusedImports;
    private volatile List<HighlightInfo> myUnusedDeclarations;

    private HighlightInfoType myUnusedHighlightType;

    public GroovyPostHighlightingPass(GroovyFile file, Editor editor) {
        super(file.getProject(), editor.getDocument(), true);
        myFile = file;
        myEditor = editor;
    }

    @Override
    public void doCollectInformation(@Nonnull final ProgressIndicator progress) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        VirtualFile virtualFile = myFile.getViewProvider().getVirtualFile();
        if (!fileIndex.isInContent(virtualFile)) {
            return;
        }

        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
        final HighlightDisplayKey unusedDefKey = HighlightDisplayKey.find(GroovyUnusedDeclarationInspection.SHORT_NAME);

        myUnusedHighlightType = new HighlightInfoTypeSeverityByKey(unusedDefKey, HighlightInfoType.RAW_UNUSED_SYMBOL.getAttributesKey());

        final boolean deadCodeEnabled = profile.isToolEnabled(unusedDefKey, myFile);
        final UnusedDeclarationInspectionBase deadCodeInspection = (UnusedDeclarationInspectionBase) profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);
        final UnusedDeclarationInspectionState declarationInspectionState = (UnusedDeclarationInspectionState) profile.getToolState(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);
        final GlobalUsageHelper usageHelper = new GlobalUsageHelper() {
            @Override
            public boolean isCurrentFileAlreadyChecked() {
                return false;
            }

            @Override
            public boolean isLocallyUsed(@Nonnull PsiNamedElement member) {
                return false;
            }

            @Override
            public boolean shouldCheckUsages(@Nonnull PsiMember member) {
                return deadCodeInspection == null || !deadCodeInspection.isEntryPoint(member, declarationInspectionState);
            }
        };

        final List<HighlightInfo> unusedDeclarations = new ArrayList<HighlightInfo>();

        final Map<GrParameter, Boolean> usedParams = new HashMap<GrParameter, Boolean>();
        myFile.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof GrReferenceElement) {
                    for (GroovyResolveResult result : ((GrReferenceElement) element).multiResolve(true)) {
                        PsiElement resolved = result.getElement();
                        if (resolved instanceof GrParameter && resolved.getContainingFile() == myFile) {
                            usedParams.put((GrParameter) resolved, Boolean.TRUE);
                        }
                    }
                }

                if (deadCodeEnabled &&
                    element instanceof GrNamedElement && element instanceof PsiModifierListOwner &&
                    !UnusedSymbolUtil.isImplicitUsage(element.getProject(), (PsiModifierListOwner) element,
                        progress) &&
                    !GroovySuppressableInspectionTool.isElementToolSuppressedIn(element,
                        GroovyUnusedDeclarationInspection.SHORT_NAME)) {
                    PsiElement nameId = ((GrNamedElement) element).getNameIdentifierGroovy();
                    if (nameId.getNode().getElementType() == GroovyTokenTypes.mIDENT) {
                        String name = ((GrNamedElement) element).getName();
                        if (element instanceof GrTypeDefinition && !UnusedSymbolUtil.isClassUsed(myProject,
                            element.getContainingFile(), (GrTypeDefinition) element, progress, usageHelper)) {
                            HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(nameId,
                                "Class " + name + " is unused", myUnusedHighlightType);
                            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                                .createSafeDeleteFix(element), unusedDefKey);
                            ContainerUtil.addIfNotNull(unusedDeclarations, highlightInfo);
                        }
                        else if (element instanceof GrMethod) {
                            GrMethod method = (GrMethod) element;
                            if (!UnusedSymbolUtil.isMethodReferenced(method.getProject(), method.getContainingFile(),
                                method, progress, usageHelper)) {
                                String message = (method.isConstructor() ? "Constructor" : "Method") + " " + name + " " +
                                    "is unused";
                                HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(nameId, message, myUnusedHighlightType);
                                QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                                    .createSafeDeleteFix(method), unusedDefKey);
                                ContainerUtil.addIfNotNull(unusedDeclarations, highlightInfo);
                            }
                        }
                        else if (element instanceof GrField && isFieldUnused((GrField) element, progress, usageHelper)) {
                            HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(nameId,
                                "Property " + name + " is unused", myUnusedHighlightType);
                            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                                .createSafeDeleteFix(element), unusedDefKey);
                            ContainerUtil.addIfNotNull(unusedDeclarations, highlightInfo);
                        }
                        else if (element instanceof GrParameter) {
                            if (!usedParams.containsKey(element)) {
                                usedParams.put((GrParameter) element, Boolean.FALSE);
                            }
                        }
                    }
                }

                super.visitElement(element);
            }
        });
        final Set<GrImportStatement> unusedImports = new HashSet<GrImportStatement>(PsiUtil.getValidImportStatements(myFile));
        unusedImports.removeAll(GroovyImportUtil.findUsedImports(myFile));
        myUnusedImports = unusedImports;

        if (deadCodeEnabled) {
            for (GrParameter parameter : usedParams.keySet()) {
                if (usedParams.get(parameter)) {
                    continue;
                }

                PsiElement scope = parameter.getDeclarationScope();
                if (scope instanceof GrMethod) {
                    GrMethod method = (GrMethod) scope;
                    if (methodMayHaveUnusedParameters(method)) {
                        PsiElement identifier = parameter.getNameIdentifierGroovy();
                        HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier,
                            "Parameter " + parameter.getName() + " is unused", myUnusedHighlightType);
                        QuickFixAction.registerQuickFixAction(highlightInfo, GroovyQuickFixFactory.getInstance()
                            .createRemoveUnusedGrParameterFix(parameter), unusedDefKey);
                        ContainerUtil.addIfNotNull(unusedDeclarations, highlightInfo);
                    }
                }
                else if (scope instanceof GrClosableBlock) {
                    //todo Max Medvedev
                }
            }
        }
        myUnusedDeclarations = unusedDeclarations;
    }

    private static boolean methodMayHaveUnusedParameters(GrMethod method) {
        return (method.isConstructor() ||
            method.hasModifierProperty(PsiModifier.PRIVATE) ||
            method.hasModifierProperty(PsiModifier.STATIC) ||
            !method.hasModifierProperty(PsiModifier.ABSTRACT) && !isOverriddenOrOverrides(method)) &&
            !method.hasModifierProperty(PsiModifier.NATIVE) &&
            !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
            !PsiClassImplUtil.isMainOrPremainMethod(method);
    }

    private static boolean isFieldUnused(GrField field, ProgressIndicator progress, GlobalUsageHelper usageHelper) {
        if (!UnusedSymbolUtil.isFieldUnused(field.getProject(), field.getContainingFile(), field, progress, usageHelper)) {
            return false;
        }
        final GrAccessorMethod[] getters = field.getGetters();
        final GrAccessorMethod setter = field.getSetter();

        for (GrAccessorMethod getter : getters) {
            if (getter.findSuperMethods().length > 0) {
                return false;
            }
        }

        if (setter != null) {
            if (setter.findSuperMethods().length > 0) {
                return false;
            }
        }

        return true;
    }

    private static boolean isOverriddenOrOverrides(PsiMethod method) {
        boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
        return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
    }

    @Override
    @RequiredReadAction
    public void doApplyInformationToEditor() {
        if (myUnusedDeclarations == null || myUnusedImports == null) {
            return;
        }

        List<HighlightInfo> infos = new ArrayList<>(myUnusedDeclarations);
        for (GrImportStatement unusedImport : myUnusedImports) {
            HighlightInfo info = HighlightInfo.newHighlightInfo(myUnusedHighlightType)
                .range(calculateRangeToUse(unusedImport))
                .descriptionAndTooltip(GroovyInspectionLocalize.unusedImport())
                .create();

            QuickFixAction.registerQuickFixAction(info, GroovyQuickFixFactory.getInstance().createOptimizeImportsFix(false));
            infos.add(info);
        }

        UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos,
            getColorsScheme(), getId());

        if (myUnusedImports != null && !myUnusedImports.isEmpty()) {
            IntentionAction fix = GroovyQuickFixFactory.getInstance().createOptimizeImportsFix(true);
            if (fix.isAvailable(myProject, myEditor, myFile) && myFile.isWritable()) {
                fix.invoke(myProject, myEditor, myFile);
            }
        }
    }

    @RequiredReadAction
    private static TextRange calculateRangeToUse(GrImportStatement unusedImport) {
        final TextRange range = unusedImport.getTextRange();

        if (StringUtil.isEmptyOrSpaces(unusedImport.getAnnotationList().getText())) {
            return range;
        }

        int start = 0;
        for (PsiElement child = unusedImport.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == GroovyTokenTypes.kIMPORT) {
                start = child.getTextRange().getStartOffset();
            }
        }
        return new TextRange(start, range.getEndOffset());
    }
}
