package org.jetbrains.plugins.groovy.impl.springloaded;

import com.intellij.java.debugger.PositionManager;
import com.intellij.java.debugger.PositionManagerFactory;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;

/**
 * Factory for position manager to debug classes reloaded by com.springsource.springloaded
 *
 * @author Sergey Evdokimov
 */
@ExtensionImpl(order = "after groovyPositionManager")
public class SpringLoadedPositionManagerFactory extends PositionManagerFactory {
  @Override
  public PositionManager createPositionManager(DebugProcess process) {
    SpringLoadedPositionManager manager = AccessRule.read(() ->
                                                          {
                                                            JavaPsiFacade facade = JavaPsiFacade.getInstance(process.getProject());
                                                            if (facade.findPackage("com.springsource.loaded") != null || facade.findPackage(
                                                              "org.springsource.loaded") != null) {
                                                              return new SpringLoadedPositionManager(process);
                                                            }
                                                            return null;
                                                          });

    if (manager != null) {
      return manager;
    }

    try {
      // Check spring loaded for remote process
      if (process.getVirtualMachineProxy()
                 .classesByName("com.springsource.loaded.agent.SpringLoadedAgent")
                 .size() > 0 || process.getVirtualMachineProxy()
                                       .classesByName("org.springsource.loaded.agent.SpringLoadedAgent")
                                       .size() > 0) {
        return new SpringLoadedPositionManager(process);
      }
    }
    catch (Exception ignored) {
      // Some problem with virtual machine.
    }

    return null;
  }
}
