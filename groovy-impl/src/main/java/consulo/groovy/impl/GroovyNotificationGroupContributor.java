package consulo.groovy.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;
import org.jetbrains.plugins.groovy.dsl.DslErrorReporterImpl;
import org.jetbrains.plugins.groovy.ivy.IvyAttachSourceProvider;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 19/02/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public class GroovyNotificationGroupContributor implements NotificationGroupContributor {

  @Override
  public void contribute(@Nonnull Consumer<NotificationGroup> consumer) {
    consumer.accept(DslErrorReporterImpl.NOTIFICATION_GROUP);
    consumer.accept(IvyAttachSourceProvider.GROOVY_IVY);
  }
}
