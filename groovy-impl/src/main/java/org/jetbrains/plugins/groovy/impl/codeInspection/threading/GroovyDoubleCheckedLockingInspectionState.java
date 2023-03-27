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
public class GroovyDoubleCheckedLockingInspectionState implements InspectionToolState<GroovyDoubleCheckedLockingInspectionState> {
  public boolean ignoreOnVolatileVariables = false;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore double-checked locking on volatile fields"),
                     () -> ignoreOnVolatileVariables,
                     b -> ignoreOnVolatileVariables = b);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public GroovyDoubleCheckedLockingInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(GroovyDoubleCheckedLockingInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
