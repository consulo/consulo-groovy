package org.jetbrains.plugins.groovy.module.extension;

import org.jetbrains.annotations.NotNull;
import consulo.extension.impl.ModuleExtensionImpl;
import consulo.module.extension.ModuleExtension;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyModuleExtension extends ModuleExtensionImpl<GroovyModuleExtension> implements ModuleExtension<GroovyModuleExtension>
{
	public GroovyModuleExtension(@NotNull String id, @NotNull ModuleRootLayer module)
	{
		super(id, module);
	}
}
