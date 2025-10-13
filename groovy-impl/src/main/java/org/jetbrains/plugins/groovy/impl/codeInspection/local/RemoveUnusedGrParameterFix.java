package org.jetbrains.plugins.groovy.impl.codeInspection.local;

import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrChangeInfoImpl;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrChangeSignatureProcessor;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrParameterInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class RemoveUnusedGrParameterFix implements IntentionAction {
    private String myName;

    public RemoveUnusedGrParameterFix(GrParameter parameter) {
        myName = parameter.getName();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.removeParameter0(myName);
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
        GrParameter parameter = PsiTreeUtil.getParentOfType(at, GrParameter.class);

        return parameter != null && myName.equals(parameter.getName());
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
        GrParameter parameter = PsiTreeUtil.getParentOfType(at, GrParameter.class);
        if (parameter == null) {
            return;
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(parameter.getContainingFile())) {
            return;
        }

        GrMethod method = (GrMethod) parameter.getDeclarationScope();
        GrChangeSignatureProcessor processor = new GrChangeSignatureProcessor(parameter.getProject(), createChangeInfo(method, parameter));
        processor.run();
    }

    private static GrChangeInfoImpl createChangeInfo(GrMethod method, GrParameter parameter) {
        List<GrParameterInfo> params = new ArrayList<GrParameterInfo>();
        int i = 0;
        for (GrParameter p : method.getParameterList().getParameters()) {
            if (p != parameter) {
                params.add(new GrParameterInfo(p, i));
            }
            i++;
        }

        GrTypeElement typeElement = method.getReturnTypeElementGroovy();
        CanonicalTypes.Type wrapper = typeElement != null ? CanonicalTypes.createTypeWrapper(method.getReturnType()) : null;
        return new GrChangeInfoImpl(method, null, wrapper, method.getName(), params, null, false);
    }


    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
