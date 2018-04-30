package org.jetbrains.plugins.groovy.mvc;

import javax.annotation.Nonnull;
import javax.swing.event.HyperlinkEvent;

import javax.annotation.Nullable;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.annotations.RequiredReadAction;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator implements StartupActivity, DumbAware
{
	@Override
	public void runActivity(final Project project)
	{
		ProgressIndicatorUtils.scheduleWithWriteActionPriority(new ReadTask()
		{
			@RequiredReadAction
			@Override
			public void computeInReadAction(@Nonnull ProgressIndicator indicator) throws ProcessCanceledException
			{
				if(JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project)) == null)
				{
					return; // If indexes is corrupted JavaPsiFacade.findClass() can't find classes during StartupActivity (may be it's a bug).
					// So we can't determine whether exists Grails library or not.
				}

				Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);

				if(pair != null)
				{
					final MvcFramework framework = pair.second;
					final Module module = pair.first;

					new Notification(framework.getFrameworkName() + ".Configure", framework.getFrameworkName() + " SDK not found.", "<html><body>Module '" +
							module.getName() +
							"' has no " +
							framework.getFrameworkName() +
							" SDK. <a href='create'>Configure SDK</a></body></html>", NotificationType.INFORMATION, new NotificationListener()
					{
						@Override
						public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event)
						{
							//MvcConfigureNotification.configure(framework, module);
						}
					}).notify(project);
				}
			}

			@Override
			public void onCanceled(@Nonnull ProgressIndicator progressIndicator)
			{
				ProgressIndicatorUtils.scheduleWithWriteActionPriority(this);
			}
		});
	}

	@Nullable
	private static Pair<Module, MvcFramework> findModuleWithoutLibrary(Project project)
	{
		MvcFramework[] frameworks = MvcFramework.EP_NAME.getExtensions();

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
