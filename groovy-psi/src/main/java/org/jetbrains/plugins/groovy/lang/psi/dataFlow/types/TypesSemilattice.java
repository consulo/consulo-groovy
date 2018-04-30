package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import java.util.ArrayList;

import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;
import com.intellij.psi.PsiManager;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState>
{
  private final PsiManager myManager;

  public TypesSemilattice(PsiManager manager) {
    myManager = manager;
  }

  public TypeDfaState join(ArrayList<TypeDfaState> ins) {
    if (ins.size() == 0) return new TypeDfaState();

    TypeDfaState result = new TypeDfaState(ins.get(0));
    for (int i = 1; i < ins.size(); i++) {
      result.joinState(ins.get(i), myManager);
    }
    return result;
  }

  public boolean eq(TypeDfaState e1, TypeDfaState e2) {
    return e1.contentsEqual(e2);
  }
}
