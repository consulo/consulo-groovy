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

package org.jetbrains.plugins.groovy.impl.dsl.holders;

import com.intellij.java.language.psi.PsiMember;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public class DelegatedMembersHolder implements CustomMembersHolder {

  private final Set<PsiMember> myMembers = new HashSet<PsiMember>();

  public void addMember(PsiMember member)  {
    myMembers.add(member);
  }

  @Override
  public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
    for (PsiMember member : myMembers) {
      if (!processor.execute(member, ResolveState.initial())) {
        return false;
      }
    }
    return true;
  }

}
