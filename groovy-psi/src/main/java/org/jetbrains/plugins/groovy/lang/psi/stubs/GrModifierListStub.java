/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierListStub extends StubBase<GrModifierList> implements StubElement<GrModifierList>
{
	private final int myFlags;

	public GrModifierListStub(StubElement parent, IStubElementType elementType, int flags)
	{
		super(parent, elementType);
		this.myFlags = flags;
	}

	public int getModifiersFlags()
	{
		return myFlags;
	}

	public static int buildFlags(GrModifierList modifierList)
	{
		int flags = 0;
		for(ObjectIntMap.Entry<String> entry : GrModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP.entrySet())
		{
			String key = entry.getKey();
			int value = entry.getValue();

			if(modifierList.hasExplicitModifier(key))
			{
				flags |= value;
			}
		}
		return flags;
	}
}
