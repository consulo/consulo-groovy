package org.jetbrains.plugins.groovy.extensions;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface GroovyClassDescriptor {

  public static final ExtensionPointName<GroovyClassDescriptor> EP_NAME = ExtensionPointName.create(GroovyClassDescriptor.class);

  @Nonnull
  String getClassName();

  @Nonnull
  Set<GroovyMethodDescriptor> getMethods();
}
