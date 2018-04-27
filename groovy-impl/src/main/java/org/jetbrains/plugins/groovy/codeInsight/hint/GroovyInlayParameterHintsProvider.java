package org.jetbrains.plugins.groovy.codeInsight.hint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import com.intellij.codeInsight.hints.InlayInfo;
import com.intellij.codeInsight.hints.InlayParameterHintsProvider;
import com.intellij.codeInsight.hints.MethodInfo;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;

/**
 * from kotlin
 */
public class GroovyInlayParameterHintsProvider implements InlayParameterHintsProvider
{
	private static Set<String> ourDefaultBlackList = ContainerUtil.newHashSet("org.codehaus.groovy.runtime.DefaultGroovyMethods.*");

	@NotNull
	@Override
	public List<InlayInfo> getParameterHints(@NotNull PsiElement element)
	{
		if(!(element instanceof GrCall))
		{
			return Collections.emptyList();
		}
		VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
		if(virtualFile != null && "gradle".equals(virtualFile.getExtension()))
		{
			return ContainerUtil.emptyList();
		}
		List<InlayInfo> inlayInfos = doGetParameterHints((GrCall) element);
		return inlayInfos == null ? Collections.<InlayInfo>emptyList() : inlayInfos;
	}

	// leave only regular literal arguments and varargs which contain literals
	private static boolean shouldShowHint(GrClosureSignatureUtil.ArgInfo<PsiElement> thisArg, GrClosableBlock closureArgument)
	{
		if(ContainerUtil.find(thisArg.args, t -> t instanceof GrLiteral || t instanceof GrClosableBlock) == null)  // do not show non-literals
		{
			return false;
		}

		if(thisArg.isMultiArg)
		{
			return ContainerUtil.find(thisArg.args, t -> t instanceof GrNamedArgument) == null;  //  do not show named arguments
		}

		if(closureArgument == null)
		{
			return true;
		}
		return !thisArg.args.contains(closureArgument); // do not show closure argument
	}

	@Nullable
	private static List<InlayInfo> doGetParameterHints(GrCall call)
	{
		GrClosureSignature signature = GrClosureSignatureUtil.createSignature(call);
		if(signature == null)
		{
			return null;
		}
		GrClosureSignatureUtil.ArgInfo<PsiElement>[] infos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);
		if(infos == null)
		{
			return null;
		}
		Iterable<Pair<GrClosureParameter, GrClosureSignatureUtil.ArgInfo<PsiElement>>> original = ContainerUtil.zip(Arrays.asList(signature.getParameters()), Arrays.asList(infos));
		GrClosableBlock closureArgument = call.getClosureArguments().length == 1 ? call.getClosureArguments()[0] : null;

		// leave only parameters with names
		List<Pair<String, GrClosureSignatureUtil.ArgInfo<PsiElement>>> filtered = ContainerUtil.mapNotNull(original, it ->
		{
			GrClosureParameter parameter = it.getFirst();
			GrClosureSignatureUtil.ArgInfo<PsiElement> info = it.getSecond();
			String name = parameter.getName();
			if(name != null && shouldShowHint(info, closureArgument))
			{
				return Pair.create(name, info);
			}
			return null;
		});

		return ContainerUtil.mapNotNull(filtered, it ->
		{
			String name = it.getFirst();
			GrClosureSignatureUtil.ArgInfo<PsiElement> info = it.getSecond();

			PsiElement psiElement = info.args.size() == 1 ? info.args.get(0) : null;
			if(psiElement != null)
			{
				String inlayText = info.isMultiArg ? "..." + name : name;
				return new InlayInfo(inlayText, psiElement.getTextOffset());
			}
			return null;
		});
	}

	@Nullable
	@Override
	public MethodInfo getMethodInfo(@NotNull PsiElement element)
	{
		if(!(element instanceof GrCall))
		{
			return null;
		}
		PsiMethod resolved = ((GrCall) element).resolveMethod();
		if(resolved == null)
		{
			return null;
		}

		PsiMethod target = resolved;
		if(resolved instanceof PsiMirrorElement)
		{
			PsiElement prototype = ((PsiMirrorElement) resolved).getPrototype();
			if(prototype instanceof PsiMethod)
			{
				target = (PsiMethod) prototype;
			}
		}
		return getMethodInfo(target);
	}

	private static MethodInfo getMethodInfo(PsiMethod method)
	{
		PsiClass containingClass = method.getContainingClass();
		if(containingClass == null)
		{
			return null;
		}
		String clazzName = containingClass.getQualifiedName();
		if(clazzName == null)
		{
			return null;
		}
		String fullMethodName = StringUtil.getQualifiedName(clazzName, method.getName());
		List<String> paramNames = Arrays.stream(method.getParameterList().getParameters()).map(it -> StringUtil.notNullize(it.getName())).collect(Collectors.toList());
		return new MethodInfo(fullMethodName, paramNames);
	}

	@NotNull
	@Override
	public Set<String> getDefaultBlackList()
	{
		return ourDefaultBlackList;
	}

	@Nullable
	@Override
	public Language getBlackListDependencyLanguage()
	{
		return JavaLanguage.INSTANCE;
	}
}