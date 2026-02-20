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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.java.language.psi.PsiNameHelper;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrReferenceListElementType<T extends GrReferenceList> extends
		GrStubElementType<GrReferenceListStub, T>
{

	public GrReferenceListElementType(String debugName)
	{
		super(debugName);
	}

	@Override
	public GrReferenceListStub createStub(@Nonnull T psi, StubElement parentStub)
	{
		List<String> refNames = new ArrayList<String>();
		for(GrCodeReferenceElement element : psi.getReferenceElementsGroovy())
		{
			String name = element.getText();
			if(StringUtil.isNotEmpty(name))
			{
				refNames.add(name);
			}
		}
		return new GrReferenceListStub(parentStub, this, ArrayUtil.toStringArray(refNames));

	}

	@Override
	public void serialize(@Nonnull GrReferenceListStub stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
		GrStubUtils.writeStringArray(dataStream, stub.getBaseClasses());
	}

	@Override
	@Nonnull
	public GrReferenceListStub deserialize(@Nonnull StubInputStream dataStream,
			StubElement parentStub) throws IOException
	{
		return new GrReferenceListStub(parentStub, this, GrStubUtils.readStringArray(dataStream));
	}

	@Override
	public void indexStub(@Nonnull GrReferenceListStub stub, @Nonnull IndexSink sink)
	{
		for(String name : stub.getBaseClasses())
		{
			if(name != null)
			{
				sink.occurrence(GrDirectInheritorsIndex.KEY, PsiNameHelper.getShortClassName(name));
			}
		}
	}

	@Override
	public boolean isLeftBound()
	{
		return true;
	}
}

