package org.jetbrains.plugins.groovy.extensions;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface GroovyMethodDescriptorExtension {
  public static final ExtensionPointName<GroovyMethodDescriptorExtension> EP_NAME =
    ExtensionPointName.create(GroovyMethodDescriptorExtension.class);

  @Nullable
  String getClassName();

  @Nullable
  String getLightMethodKey();

  @Nonnull
  GroovyMethodDescriptor getMethodDescriptor();
}
