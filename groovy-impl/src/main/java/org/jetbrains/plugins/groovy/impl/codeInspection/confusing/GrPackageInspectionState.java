package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

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
public class GrPackageInspectionState implements InspectionToolState<GrPackageInspectionState>
{
	public boolean myCheckScripts = true;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(GroovyInspectionLocalize.grPackageInspectionCheckScripts(), () -> myCheckScripts, b -> myCheckScripts = b);
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public GrPackageInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(GrPackageInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
