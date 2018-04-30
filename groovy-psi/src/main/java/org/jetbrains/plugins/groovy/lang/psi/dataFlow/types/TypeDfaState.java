/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;

class TypeDfaState {
  private final Map<String, DFAType> myVarTypes;

  TypeDfaState() {
    myVarTypes = ContainerUtil.newHashMap();
  }

  TypeDfaState(TypeDfaState another) {
    myVarTypes = ContainerUtil.newHashMap(another.myVarTypes);
  }

  TypeDfaState mergeWith(TypeDfaState another) {
    if (another.myVarTypes.isEmpty()) {
      return this;
    }
    TypeDfaState state = new TypeDfaState(this);
    state.myVarTypes.putAll(another.myVarTypes);
    return state;
  }

  void joinState(TypeDfaState another, PsiManager manager) {
    for (Map.Entry<String, DFAType> entry : another.myVarTypes.entrySet()) {
      final String name = entry.getKey();
      final DFAType t1 = entry.getValue();
      if (myVarTypes.containsKey(name)) {
        final DFAType t2 = myVarTypes.get(name);
        if (t1 != null && t2 != null) {
          myVarTypes.put(name, DFAType.create(t1, t2, manager));
        }
        else {
          myVarTypes.put(name, null);
        }
      }
    }
  }

  boolean contentsEqual(TypeDfaState another) {
    return myVarTypes.equals(another.myVarTypes);
  }

  @javax.annotation.Nullable
  DFAType getVariableType(String variableName) {
    return myVarTypes.get(variableName);
  }

  Map<String, PsiType> getBindings(Instruction instruction) {
    HashMap<String,PsiType> map = ContainerUtil.newHashMap();
    for (Map.Entry<String, DFAType> entry : myVarTypes.entrySet()) {
      DFAType value = entry.getValue();
      map.put(entry.getKey(), value == null ? null : value.negate(instruction).getResultType());
    }
    return map;
  }

  void putType(String variableName, @javax.annotation.Nullable DFAType type) {
    myVarTypes.put(variableName, type);
  }

  @Override
  public String toString() {
    return "TypeDfaState{" + myVarTypes + '}';
  }

  public boolean containsVariable(@Nonnull String variableName) {
    return myVarTypes.containsKey(variableName);
  }

  public void removeBinding(String variableName) {
    myVarTypes.remove(variableName);
  }
}