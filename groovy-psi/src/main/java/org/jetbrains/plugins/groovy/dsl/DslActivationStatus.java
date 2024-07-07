/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.*;
import consulo.ide.ServiceManager;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

import java.util.*;

@Singleton
@State(
  name = "DslActivationStatus",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/dslActivation.xml",
      roamingType = RoamingType.DISABLED, deprecated = true),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/dslActivationStatus.xml",
      roamingType = RoamingType.DISABLED)
  })
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class DslActivationStatus implements PersistentStateComponent<DslActivationStatus.State> {
  @Nonnull
  public static DslActivationStatus getInstance() {
    return ServiceManager.getService(DslActivationStatus.class);
  }

  public enum Status {
    ACTIVE,
    MODIFIED,
    ERROR
  }

  public static class Entry implements Comparable<Entry> {
    @Attribute
    public String url;
    @Attribute
    public Status status;
    @Attribute
    public String error;

    public Entry() {
    }

    public Entry(String url, Status status, String error) {
      this.url = url;
      this.status = status;
      this.error = error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Entry entry = (Entry)o;

      if (url != null ? !url.equals(entry.url) : entry.url != null) {
        return false;
      }
      if (status != entry.status) {
        return false;
      }
      if (error != null ? !error.equals(entry.error) : entry.error != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = url != null ? url.hashCode() : 0;
      result = 31 * result + (status != null ? status.hashCode() : 0);
      result = 31 * result + (error != null ? error.hashCode() : 0);
      return result;
    }

    @Override
    public int compareTo(Entry o) {
      return equals(o) ? 0 : url.compareTo(o.url);
    }
  }

  public static class State {
    @AbstractCollection(surroundWithTag = false)
    public Collection<Entry> entries;

    public State(@Nonnull Collection<Entry> entries) {
      this.entries = entries;
    }

    public State() {
      this(new SmartList<Entry>());
    }
  }

  private final Map<VirtualFile, Entry> myStatus = new HashMap<VirtualFile, Entry>();

  @Nullable
  public Entry getGdslFileInfo(@Nonnull VirtualFile file) {
    synchronized (myStatus) {
      return myStatus.get(file);
    }
  }

  @Nonnull
  public Entry getGdslFileInfoOrCreate(@Nonnull VirtualFile file) {
    Entry entry;
    synchronized (myStatus) {
      entry = myStatus.get(file);
      if (entry == null) {
        entry = new Entry(file.getUrl(), Status.ACTIVE, null);
        myStatus.put(file, entry);
      }
    }
    return entry;
  }

  @Nullable
  @Override
  public State getState() {
    synchronized (myStatus) {
      // remove default entries
      myStatus.entrySet().removeIf(entry -> entry.getValue().status == Status.ACTIVE && entry.getValue().error == null);

      if (myStatus.isEmpty()) {
        return new State(Collections.<Entry>emptyList());
      }

      Entry[] entries = myStatus.values().toArray(new Entry[myStatus.size()]);
      Arrays.sort(entries);
      return new State(Arrays.asList(entries));
    }
  }

  @Override
  public void loadState(State state) {
    synchronized (myStatus) {
      myStatus.clear();
      if (ContainerUtil.isEmpty(state.entries)) {
        return;
      }

      final VirtualFileManager fileManager = VirtualFileManager.getInstance();
      for (Entry entry : state.entries) {
        if (entry.url == null || entry.status == null) {
          continue;
        }
        final VirtualFile file = fileManager.findFileByUrl(entry.url);
        if (file != null) {
          myStatus.put(file, entry);
        }
      }
    }
  }
}
