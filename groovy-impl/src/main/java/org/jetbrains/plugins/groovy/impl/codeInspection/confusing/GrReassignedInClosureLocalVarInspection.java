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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReassignedLocalVarsChecker;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class GrReassignedInClosureLocalVarInspection extends BaseInspection
{

	@Override
	@Nls
	@Nonnull
	public String getGroupDisplayName()
	{
		return CONFUSING_CODE_CONSTRUCTS;
	}

	@Override
	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return "Local variable is reassigned in closure or anonymous class";
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return true;
	}

	@Nonnull
	@Override
	protected BaseInspectionVisitor buildVisitor()
	{
		return new BaseInspectionVisitor()
		{
			@Override
			public void visitReferenceExpression(GrReferenceExpression referenceExpression)
			{
				super.visitReferenceExpression(referenceExpression);

				if(!PsiUtil.isLValue(referenceExpression))
				{
					return;
				}
				final PsiElement resolved = referenceExpression.resolve();
				if(!PsiUtil.isLocalVariable(resolved))
				{
					return;
				}

				final PsiType checked = GrReassignedLocalVarsChecker.getReassignedVarType(referenceExpression, false);
				if(checked == null)
				{
					return;
				}

				final GrControlFlowOwner varFlowOwner = ControlFlowUtils.findControlFlowOwner(resolved);
				final GrControlFlowOwner refFlorOwner = ControlFlowUtils.findControlFlowOwner(referenceExpression);
				if(isOtherScopeAndType(referenceExpression, checked, varFlowOwner, refFlorOwner))
				{
					String flowDescription = getFlowDescription(refFlorOwner);
					final String message = GroovyInspectionBundle.message("local.var.0.is.reassigned",
							((GrNamedElement) resolved).getName(), flowDescription);
					registerError(referenceExpression, message, LocalQuickFix.EMPTY_ARRAY,
							ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
				}
			}
		};
	}

	private static boolean isOtherScopeAndType(GrReferenceExpression referenceExpression,
			PsiType checked,
			GrControlFlowOwner varFlowOwner,
			GrControlFlowOwner refFlorOwner)
	{
		return varFlowOwner != refFlorOwner && !TypesUtil.isAssignable(referenceExpression.getType(), checked,
				referenceExpression);
	}

	private static String getFlowDescription(GrControlFlowOwner refFlorOwner)
	{
		String flowDescription;
		if(refFlorOwner instanceof GrClosableBlock)
		{
			flowDescription = GroovyInspectionBundle.message("closure");
		}
		else if(refFlorOwner instanceof GrAnonymousClassDefinition)
		{
			flowDescription = GroovyInspectionBundle.message("anonymous.class");
		}
		else
		{
			flowDescription = GroovyInspectionBundle.message("other.scope");
		}
		return flowDescription;
	}
}
