/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.impl.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;

/**
 * @author ilyas
 */

@State(
  name = "GroovyApplicationSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/groovy_config.xml"
    )}
)
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class GroovyApplicationSettings implements PersistentStateComponent<GroovyApplicationSettings> {

  public boolean INTRODUCE_LOCAL_CREATE_FINALS = false;
  public boolean INTRODUCE_LOCAL_SELECT_DEF = true;
  public boolean FORCE_RETURN = false;
  public Boolean EXTRACT_METHOD_SPECIFY_TYPE = null;
  public String EXTRACT_METHOD_VISIBILITY = null;
  public Boolean CONVERT_PARAM_SPECIFY_MAP_TYPE = null;
  public Boolean CONVERT_PARAM_CREATE_NEW_PARAM = null;

  public GroovyApplicationSettings getState() {
    return this;
  }

  public void loadState(GroovyApplicationSettings groovyApplicationSettings) {
    XmlSerializerUtil.copyBean(groovyApplicationSettings, this);
  }

  public static GroovyApplicationSettings getInstance() {
    return ServiceManager.getService(GroovyApplicationSettings.class);
  }

}
