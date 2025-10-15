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
package org.jetbrains.plugins.groovy.impl.actions.generate.constructors;

import com.intellij.java.impl.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.java.impl.codeInsight.generation.PsiFieldMember;
import com.intellij.java.impl.codeInsight.generation.PsiGenerationInfo;
import com.intellij.java.impl.codeInsight.generation.PsiMethodMember;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.generation.ClassMember;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.actions.generate.GroovyGenerationInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry.Krasilschikov
 * @since 2008-05-21
 */
public class GroovyGenerateConstructorHandler extends GenerateConstructorHandler {
    private static final String DEF_PSEUDO_ANNO = "_____intellij_idea_rulez_def_";

    @Nullable
    @Override
    protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
        ClassMember[] classMembers = chooseOriginalMembersImpl(aClass, project);
        if (classMembers == null) {
            return null;
        }

        List<ClassMember> res = new ArrayList<>();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        for (ClassMember classMember : classMembers) {
            if (classMember instanceof PsiMethodMember methodMember) {
                PsiMethod method = methodMember.getElement();

                PsiMethod copy = factory.createMethodFromText(GroovyToJavaGenerator.generateMethodStub(method), method);
                if (method instanceof GrMethod grMethod) {
                    GrParameter[] parameters = grMethod.getParameterList().getParameters();
                    PsiParameter[] copyParameters = copy.getParameterList().getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].getTypeElementGroovy() == null) {
                            copyParameters[i].setName(DEF_PSEUDO_ANNO + parameters[i].getName());
                        }
                    }
                }

                res.add(new PsiMethodMember(copy));
            }
            else if (classMember instanceof PsiFieldMember fieldMember) {
                PsiField field = fieldMember.getElement();

                String prefix = field instanceof GrField grField && grField.getTypeElementGroovy() == null ? DEF_PSEUDO_ANNO : "";
                res.add(new PsiFieldMember(factory.createFieldFromText(
                    field.getType().getCanonicalText() + " " + prefix + field.getName(),
                    aClass
                )));
            }
        }

        return res.toArray(new ClassMember[res.size()]);
    }

    @Nullable
    protected ClassMember[] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
        return super.chooseOriginalMembers(aClass, project);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members)
        throws IncorrectOperationException {
        List<? extends GenerationInfo> list = super.generateMemberPrototypes(aClass, members);

        List<PsiGenerationInfo<GrMethod>> grConstructors = new ArrayList<>();

        Project project = aClass.getProject();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

        for (GenerationInfo generationInfo : list) {
            PsiMember constructorMember = generationInfo.getPsiMember();
            assert constructorMember instanceof PsiMethod;
            PsiMethod constructor = (PsiMethod) constructorMember;

            PsiCodeBlock block = constructor.getBody();
            assert block != null;

            String constructorName = aClass.getName();
            String body = StringUtil.replace(StringUtil.replace(block.getText(), DEF_PSEUDO_ANNO, ""), ";", "");
            PsiParameterList list1 = constructor.getParameterList();

            List<String> parametersNames = new ArrayList<>();
            List<String> parametersTypes = new ArrayList<>();
            for (PsiParameter parameter : list1.getParameters()) {
                String fullName = parameter.getName();
                parametersNames.add(StringUtil.trimStart(fullName, DEF_PSEUDO_ANNO));
                parametersTypes.add(fullName.startsWith(DEF_PSEUDO_ANNO) ? null : parameter.getType().getCanonicalText());
            }

            String[] paramNames = ArrayUtil.toStringArray(parametersNames);
            String[] paramTypes = ArrayUtil.toStringArray(parametersTypes);
            assert constructorName != null;
            GrMethod grConstructor = factory.createConstructorFromText(constructorName, paramTypes, paramNames, body);
            PsiReferenceList throwsList = grConstructor.getThrowsList();
            for (PsiJavaCodeReferenceElement element : constructor.getThrowsList().getReferenceElements()) {
                throwsList.add(element);
            }
            codeStyleManager.shortenClassReferences(grConstructor);

            grConstructors.add(new GroovyGenerationInfo<>(grConstructor));
        }

        return grConstructors;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
