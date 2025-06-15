package org.jetbrains.plugins.groovy.impl.codeInsight.hint;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.Language;
import consulo.language.editor.inlay.HintInfo;
import consulo.language.editor.inlay.InlayInfo;
import consulo.language.editor.inlay.InlayParameterHintsProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiMirrorElement;
import consulo.language.psi.PsiUtilCore;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groovy-specific implementation of InlayParameterHintsProvider.
 */
@ExtensionImpl
public class GroovyInlayParameterHintsProvider implements InlayParameterHintsProvider {
    private static final Set<String> BLACK_LIST = Collections.singleton(
        "org.codehaus.groovy.runtime.DefaultGroovyMethods.*"
    );

    @Nonnull
    @Override
    public List<InlayInfo> getParameterHints(@Nonnull PsiElement element) {
        if (!(element instanceof GrCall)) {
            return Collections.emptyList();
        }
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
        if (virtualFile != null && "gradle".equals(virtualFile.getExtension())) {
            return List.of();
        }
        List<InlayInfo> inlayInfos = doGetParameterHints((GrCall) element);
        return inlayInfos == null ? Collections.<InlayInfo>emptyList() : inlayInfos;
    }

    @Override
    public HintInfo.MethodInfo getHintInfo(@Nonnull PsiElement element) {
        if (!(element instanceof GrCall)) {
            return null;
        }
        GrCall call = (GrCall) element;
        PsiElement resolved = call.resolveMethod();
        PsiMethod method;
        if (resolved instanceof PsiMirrorElement
            && ((PsiMirrorElement) resolved).getPrototype() instanceof PsiMethod) {
            method = (PsiMethod) ((PsiMirrorElement) resolved).getPrototype();
        }
        else if (resolved instanceof PsiMethod) {
            method = (PsiMethod) resolved;
        }
        else {
            return null;
        }
        return getMethodInfo(method);
    }

    private HintInfo.MethodInfo getMethodInfo(PsiMethod method) {
        String clazzName = method.getContainingClass() != null
            ? method.getContainingClass().getQualifiedName()
            : null;
        if (clazzName == null) {
            return null;
        }
        String fullName = StringUtil.getQualifiedName(clazzName, method.getName());
        PsiParameter[] params = method.getParameterList().getParameters();
        List<String> names = Arrays.stream(params)
            .map(PsiParameter::getName)
            .collect(Collectors.toList());
        Language lang = method.getLanguage().equals(getBlackListDependencyLanguage())
            ? method.getLanguage() : null;
        return new HintInfo.MethodInfo(fullName, names, lang);
    }

    @Nonnull
    @Override
    public Set<String> getDefaultBlackList() {
        return BLACK_LIST;
    }

    @Override
    public Language getBlackListDependencyLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public LocalizeValue getPreviewFileText() {
        return LocalizeValue.localizeTODO(
            """
                static def foo(a, b) {
                  b.add(a)
                }

                static def bar() {
                  foo(1, [1])
                  foo("", [""])
                }
                        """);
    }

    @Nonnull
    @Override
    public LocalizeValue getDescription() {
        return GroovyLocalize.showsParameterNamesAtFunctionCallSites();
    }

    private static List<InlayInfo> doGetParameterHints(GrCall call) {
        GrClosureSignature signature = GrClosureSignatureUtil.createSignature(call);
        if (signature == null) {
            return null;
        }
        GrClosureSignatureUtil.ArgInfo<PsiElement>[] infos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);
        if (infos == null) {
            return null;
        }
        Iterable<Pair<GrClosureParameter, GrClosureSignatureUtil.ArgInfo<PsiElement>>> original =
            ContainerUtil.zip(Arrays.asList(signature.getParameters()), Arrays.asList(infos));
        GrClosableBlock closureArgument = call.getClosureArguments().length == 1 ? call.getClosureArguments()[0] : null;

        // leave only parameters with names
        List<Pair<String, GrClosureSignatureUtil.ArgInfo<PsiElement>>> filtered = ContainerUtil.mapNotNull(original, it ->
        {
            GrClosureParameter parameter = it.getFirst();
            GrClosureSignatureUtil.ArgInfo<PsiElement> info = it.getSecond();
            String name = parameter.getName();
            if (name != null && shouldShowHint(info, closureArgument)) {
                return Pair.create(name, info);
            }
            return null;
        });

        return ContainerUtil.mapNotNull(filtered, it -> {
            String name = it.getFirst();
            GrClosureSignatureUtil.ArgInfo<PsiElement> info = it.getSecond();

            PsiElement psiElement = info.args.size() == 1 ? info.args.get(0) : null;
            if (psiElement != null) {
                String inlayText = info.isMultiArg ? "..." + name : name;
                return new InlayInfo(inlayText, psiElement.getTextOffset());
            }
            return null;
        });
    }

    // leave only regular literal arguments and varargs which contain literals
    private static boolean shouldShowHint(GrClosureSignatureUtil.ArgInfo<PsiElement> thisArg, GrClosableBlock closureArgument) {
        if (ContainerUtil.find(thisArg.args, t -> t instanceof GrLiteral || t instanceof GrClosableBlock) == null)  // do not show non-literals
        {
            return false;
        }

        if (thisArg.isMultiArg) {
            return ContainerUtil.find(thisArg.args, t -> t instanceof GrNamedArgument) == null;  //  do not show named arguments
        }

        if (closureArgument == null) {
            return true;
        }
        return !thisArg.args.contains(closureArgument); // do not show closure argument
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return GroovyLanguage.INSTANCE;
    }
}
