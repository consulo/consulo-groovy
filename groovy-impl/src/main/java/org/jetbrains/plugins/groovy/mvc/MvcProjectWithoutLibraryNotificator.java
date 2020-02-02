package org.jetbrains.plugins.groovy.mvc;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import consulo.ui.UIAccess;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator implements StartupActivity.DumbAware
{
	@Override
	public void runActivity(UIAccess uiAccess, Project project)
	{
		ReadAction.nonBlocking(() -> {
			Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);

			if(pair != null)
			{
				final MvcFramework framework = pair.second;
				final Module module = pair.first;

				new Notification(framework.getFrameworkName() + ".Configure", framework.getFrameworkName() + " SDK not found.", "<html><body>Module '" + module.getName() + "' has no " + framework.getFrameworkName() + " SDK. <a " +
						"href='create'>Configure SDK</a></body></html>", NotificationType.INFORMATION, new NotificationListener()
				{
					@Override
					public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event)
					{
						//MvcConfigureNotification.configure(framework, module);
					}
				}).notify(project);
			}
		}).inSmartMode(project).submit(AppExecutorUtil.getAppExecutorService());
	}

	@Nullable
	private static Pair<Module, MvcFramework> findModuleWithoutLibrary(Project project)
	{
		List<MvcFramework> frameworks = MvcFramework.EP_NAME.getExtensionList();

		for(Module module : ModuleManager.getInstance(project).getModules())
		{
			for(MvcFramework framework : frameworks)
			{
				VirtualFile appRoot = framework.findAppRoot(module);
				if(appRoot != null && appRoot.findChild("application.properties") != null)
				{
					if(!framework.hasFrameworkJar(module))
					{
						return Pair.create(module, framework);
					}
				}
			}
		}

		return null;
	}
}
