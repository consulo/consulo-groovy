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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.ASTNode;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nonnull;

/**
 * @author ilyas
 */
public class GrBuiltInTypeElementImpl extends GroovyPsiElementImpl implements GrBuiltInTypeElement
{

	public GrBuiltInTypeElementImpl(@Nonnull ASTNode node)
	{
		super(node);
	}

	@Override
	public void accept(GroovyElementVisitor visitor)
	{
		visitor.visitBuiltinTypeElement(this);
	}

	public String toString()
	{
		return "Built in type";
	}

	@Override
	@Nonnull
	public PsiType getType()
	{
		return TypesUtil.getPrimitiveTypeByText(getText());
	}
}
