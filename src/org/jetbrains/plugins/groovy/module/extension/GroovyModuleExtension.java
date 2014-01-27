package org.jetbrains.plugins.groovy.module.extension;

import org.consulo.module.extension.ModuleExtension;
import org.consulo.module.extension.impl.ModuleExtensionImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyModuleExtension extends ModuleExtensionImpl<GroovyModuleExtension> implements ModuleExtension<GroovyModuleExtension>
{
	public GroovyModuleExtension(@NotNull String id, @NotNull Module module)
	{
		super(id, module);
	}
}
