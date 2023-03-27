package org.jetbrains.plugins.groovy.impl.codeInspection.declaration;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 27/03/2023
 */
public class GrMethodMayBeStaticInspectionState implements InspectionToolState<GrMethodMayBeStaticInspectionState>
{
	public boolean myOnlyPrivateOrFinal = false;
	public boolean myIgnoreEmptyMethods = true;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(GroovyInspectionLocalize.methodMayBeStaticOnlyPrivateOrFinalOption(), () -> myOnlyPrivateOrFinal, b -> myOnlyPrivateOrFinal = b);
		builder.checkBox(GroovyInspectionLocalize.methodMayBeStaticIgnoreEmptyMethodOption(), () -> myIgnoreEmptyMethods, b -> myIgnoreEmptyMethods = b);
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public GrMethodMayBeStaticInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(GrMethodMayBeStaticInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
