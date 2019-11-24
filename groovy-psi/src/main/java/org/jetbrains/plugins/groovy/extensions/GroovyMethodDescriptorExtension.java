package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.util.xmlb.annotations.Attribute;
import consulo.container.plugin.IdeaPluginDescriptor;
import consulo.container.plugin.PluginDescriptor;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodDescriptorExtension extends GroovyMethodDescriptor implements PluginAware {

  public static final ExtensionPointName<GroovyMethodDescriptorExtension> EP_NAME = ExtensionPointName.create("org.intellij.groovy.methodDescriptor");

  @Attribute("class")
  public String className;

  @Attribute("lightMethodKey")
  public String lightMethodKey;

  private PluginDescriptor myPluginDescriptor;

  @Override
  public final void setPluginDescriptor(IdeaPluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public ClassLoader getLoaderForClass() {
    return myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader();
  }
}
