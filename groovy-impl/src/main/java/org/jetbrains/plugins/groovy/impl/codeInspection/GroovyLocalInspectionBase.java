/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ven
 */
public abstract class GroovyLocalInspectionBase<State> extends GroovySuppressableInspectionTool
{
	@Nullable
	@Override
	public Language getLanguage()
	{
		return GroovyLanguage.INSTANCE;
	}

	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder problemsHolder, boolean isOnTheFly, @Nonnull LocalInspectionToolSession session, @Nonnull Object state)
	{
		State inspectionState = (State) state;

		return new GroovyPsiElementVisitor(new GroovyElementVisitor()
		{
			public void visitClosure(GrClosableBlock closure)
			{
				check(closure, problemsHolder, inspectionState);
			}

			public void visitMethod(GrMethod method)
			{
				final GrOpenBlock block = method.getBlock();
				if(block != null)
				{
					check(block, problemsHolder, inspectionState);
				}
			}

			public void visitFile(GroovyFileBase file)
			{
				check(file, problemsHolder, inspectionState);
			}

			@Override
			public void visitClassInitializer(GrClassInitializer initializer)
			{
				check(initializer.getBlock(), problemsHolder, inspectionState);
			}
		});
	}

	protected abstract void check(GrControlFlowOwner owner, ProblemsHolder problemsHolder, State inspectionState);
}
