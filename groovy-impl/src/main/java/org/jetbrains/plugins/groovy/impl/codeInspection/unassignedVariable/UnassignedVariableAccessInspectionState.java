package org.jetbrains.plugins.groovy.impl.codeInspection.unassignedVariable;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 27/03/2023
 */
public class UnassignedVariableAccessInspectionState implements InspectionToolState<UnassignedVariableAccessInspectionState>
{
	public boolean myIgnoreBooleanExpressions = true;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(GroovyInspectionLocalize.ignoreBooleanExpressions(), () -> myIgnoreBooleanExpressions, b -> myIgnoreBooleanExpressions = b);
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public UnassignedVariableAccessInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(UnassignedVariableAccessInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
