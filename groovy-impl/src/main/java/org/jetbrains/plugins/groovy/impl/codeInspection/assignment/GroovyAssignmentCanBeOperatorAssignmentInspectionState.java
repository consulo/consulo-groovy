package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

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
public class GroovyAssignmentCanBeOperatorAssignmentInspectionState implements InspectionToolState<GroovyAssignmentCanBeOperatorAssignmentInspectionState> {

  public boolean ignoreLazyOperators = true;
  public boolean ignoreObscureOperators = false;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore conditional operators"), () -> ignoreLazyOperators, b -> ignoreLazyOperators = b);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore obscure operators"), () -> ignoreObscureOperators, b -> ignoreObscureOperators = b);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public GroovyAssignmentCanBeOperatorAssignmentInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(GroovyAssignmentCanBeOperatorAssignmentInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
