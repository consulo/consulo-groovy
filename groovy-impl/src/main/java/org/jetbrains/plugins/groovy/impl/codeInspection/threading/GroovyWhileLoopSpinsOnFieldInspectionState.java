package org.jetbrains.plugins.groovy.impl.codeInspection.threading;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 27/03/2023
 */
public class GroovyWhileLoopSpinsOnFieldInspectionState implements InspectionToolState<GroovyWhileLoopSpinsOnFieldInspectionState> {
  public boolean ignoreNonEmtpyLoops = false;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Only warn if loop is empty"), () -> ignoreNonEmtpyLoops, b -> ignoreNonEmtpyLoops = b);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public GroovyWhileLoopSpinsOnFieldInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(GroovyWhileLoopSpinsOnFieldInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
