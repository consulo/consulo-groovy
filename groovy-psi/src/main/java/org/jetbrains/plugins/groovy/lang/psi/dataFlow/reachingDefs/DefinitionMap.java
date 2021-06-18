/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import consulo.util.collection.primitive.ints.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * @author peter
 */
public class DefinitionMap
{
	private final IntObjectMap<IntSet> myMap = IntMaps.newIntObjectHashMap();

	public void registerDef(Instruction varInsn, int varId)
	{
		IntSet defs = myMap.get(varId);
		if(defs == null)
		{
			myMap.put(varId, defs = IntSets.newHashSet());
		}
		else
		{
			defs.clear();
		}
		defs.add(varInsn.num());
	}

	public void merge(DefinitionMap map2)
	{
		map2.myMap.forEach(new IntObjConsumer<IntSet>()
		{
			public void accept(int num, IntSet defs)
			{
				IntSet defs2 = myMap.get(num);
				if(defs2 == null)
				{
					defs2 = IntSets.newHashSet(defs.toArray());
					myMap.put(num, defs2);
				}
				else
				{
					defs2.addAll(defs.toArray());
				}
			}
		});
	}

	public boolean eq(final DefinitionMap m2)
	{
		if(myMap.size() != m2.myMap.size())
		{
			return false;
		}

		for(IntObjectMap.IntObjectEntry<IntSet> entry : myMap.entrySet())
		{
			int num = entry.getKey();
			IntSet defs1 = entry.getValue();

			final IntSet defs2 = m2.myMap.get(num);
			if(!(defs2 != null && defs2.equals(defs1)))
			{
				return false;
			}
		}
		return true;
	}

	public void copyFrom(DefinitionMap map, int fromIndex, int toIndex)
	{
		IntSet defs = map.myMap.get(fromIndex);
		if(defs == null)
		{
			defs = IntSets.newHashSet();
		}
		myMap.put(toIndex, defs);
	}

	@Nullable
	public int[] getDefinitions(int varId)
	{
		IntSet set = myMap.get(varId);
		return set == null ? null : set.toArray();
	}

	public void forEachValue(Consumer<IntSet> procedure)
	{
		myMap.values().forEach(procedure);
	}
}
