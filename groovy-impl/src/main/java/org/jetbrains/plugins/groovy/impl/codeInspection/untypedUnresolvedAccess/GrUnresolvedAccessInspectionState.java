package org.jetbrains.plugins.groovy.impl.codeInspection.untypedUnresolvedAccess;

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
public class GrUnresolvedAccessInspectionState implements InspectionToolState<GrUnresolvedAccessInspectionState>
{
	public boolean myHighlightIfGroovyObjectOverridden = true;
	public boolean myHighlightIfMissingMethodsDeclared = true;
	public boolean myHighlightInnerClasses = true;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(GroovyInspectionLocalize.highlightIfGroovyObjectMethodsOverridden(), () -> myHighlightIfGroovyObjectOverridden, b -> myHighlightIfGroovyObjectOverridden = b);
		builder.checkBox(GroovyInspectionLocalize.highlightIfMissingMethodsDeclared(), () -> myHighlightIfMissingMethodsDeclared, b -> myHighlightIfMissingMethodsDeclared = b);
		builder.checkBox(GroovyInspectionLocalize.highlightConstructorCallsOfANonStaticInnerClassesWithoutEnclosingInstancePassed(), () -> myHighlightInnerClasses, b -> myHighlightInnerClasses = b);
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public GrUnresolvedAccessInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(GrUnresolvedAccessInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
