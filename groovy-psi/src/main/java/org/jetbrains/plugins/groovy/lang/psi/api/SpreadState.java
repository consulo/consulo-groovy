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
package org.jetbrains.plugins.groovy.lang.psi.api;

import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.CollectionUtil;
import com.intellij.openapi.project.Project;
import consulo.util.dataholder.Key;
import com.intellij.psi.PsiType;

/**
 * @author Max Medvedev
 */
public final class SpreadState
{
	public static final Key<SpreadState> SPREAD_STATE = Key.create("Spread state");

	@Nullable
	private final PsiType containerType;
	@javax.annotation.Nullable
	private final SpreadState innerState;

	public SpreadState(@javax.annotation.Nullable PsiType type, @javax.annotation.Nullable SpreadState state)
	{
		containerType = type;
		innerState = state;
	}

	@javax.annotation.Nullable
	public PsiType getContainerType()
	{
		return containerType;
	}

	@javax.annotation.Nullable
	public SpreadState getInnerState()
	{
		return innerState;
	}

	public static SpreadState create(PsiType type, @Nullable SpreadState state)
	{
		return new SpreadState(type, state);
	}

	@javax.annotation.Nullable
	public static PsiType apply(@javax.annotation.Nullable PsiType item, @Nullable SpreadState state, Project project)
	{
		if(state == null)
		{
			return item;
		}
		return apply(CollectionUtil.createSimilarCollection(state.getContainerType(), project, item),
				state.getInnerState(), project);
	}
}
