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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.ast.TokenType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * @author Maxim.Medvedev
 */
public class GroovyRangeTypeCheckInspection extends BaseInspection {
    private static final Logger LOG =
        Logger.getInstance("#org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyRangeTypeCheckInspection");

    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new MyVisitor();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PROBABLE_BUGS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.incorrectRangeArgument();
    }

    @Override
    protected GroovyFix buildFix(@Nonnull PsiElement location) {
        GrRangeExpression range = (GrRangeExpression) location;
        final List<GroovyFix> fixes = new ArrayList<>(3);
        if (range.getType() instanceof GrRangeType rangeType) {
            if (!(rangeType.getIterationType() instanceof PsiClassType iterationType
                && iterationType.resolve() instanceof GrTypeDefinition typeDef)) {
                return null;
            }

            GroovyResolveResult[] nexts = ResolveUtil.getMethodCandidates(iterationType, "next", range);
            GroovyResolveResult[] previouses = ResolveUtil.getMethodCandidates(iterationType, "previous", range);
            GroovyResolveResult[] compareTos = ResolveUtil.getMethodCandidates(iterationType, "compareTo", range, iterationType);

            if (countImplementations(typeDef, nexts) == 0) {
                fixes.add(new AddMethodFix("next", typeDef));
            }
            if (countImplementations(typeDef, previouses) == 0) {
                fixes.add(new AddMethodFix("previous", typeDef));
            }

            if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE)
                || countImplementations(typeDef, compareTos) == 0) {
                fixes.add(new AddClassToExtends(typeDef, CommonClassNames.JAVA_LANG_COMPARABLE));
            }

            return new GroovyFix() {
                @Override
                protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
                    for (GroovyFix fix : fixes) {
                        fix.applyFix(project, descriptor);
                    }
                }

                @Nonnull
                @Override
                public LocalizeValue getName() {
                    return GroovyInspectionLocalize.fixClass(typeDef.getName());
                }
            };
        }
        return null;
    }

    private static int countImplementations(PsiClass clazz, GroovyResolveResult[] methods) {
        if (clazz.isInterface()) {
            return methods.length;
        }
        int result = 0;
        for (GroovyResolveResult method : methods) {
            PsiElement el = method.getElement();
            if (el instanceof PsiMethod methodEl && !methodEl.isAbstract()) {
                result++;
            }
            else if (el instanceof PsiField) {
                result++;
            }
        }
        return result;
    }

    @Override
    protected String buildErrorString(Object... args) {
        return switch (args.length) {
            case 1 -> GroovyInspectionLocalize.typeDoesntImplemntComparable(args[0]).get();
            case 2 -> GroovyInspectionLocalize.typeDoesntContainMethod(args[0], args[1]).get();
            default -> throw new IncorrectOperationException("incorrect args:" + Arrays.toString(args));
        };
    }

    private static class MyVisitor extends BaseInspectionVisitor {
        @Override
        public void visitRangeExpression(GrRangeExpression range) {
            super.visitRangeExpression(range);
            if (!(range.getType() instanceof GrRangeType rangeType)) {
                return;
            }
            PsiType iterationType = rangeType.getIterationType();
            if (iterationType == null) {
                return;
            }

            GroovyResolveResult[] nexts = ResolveUtil.getMethodCandidates(iterationType, "next", range, PsiType.EMPTY_ARRAY);
            GroovyResolveResult[] previouses = ResolveUtil.getMethodCandidates(iterationType, "previous", range, PsiType.EMPTY_ARRAY);
            if (nexts.length == 0) {
                registerError(range, iterationType.getPresentableText(), "next()");
            }
            if (previouses.length == 0) {
                registerError(range, iterationType.getPresentableText(), "previous()");
            }

            if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE)) {
                registerError(range, iterationType.getPresentableText());
            }
        }
    }

    private static class AddMethodFix extends GroovyFix {
        private final String myMethodName;
        private final GrTypeDefinition myClass;

        private AddMethodFix(String methodName, GrTypeDefinition aClass) {
            myMethodName = methodName;
            myClass = aClass;
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            if (myClass.isInterface()) {
                GrMethod method = GroovyPsiElementFactory.getInstance(project)
                    .createMethodFromText("def " + myClass.getName() + " " + myMethodName + "();");
                myClass.add(method);
            }
            else {
                String templateName = JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY;
                FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templateName);

                Properties properties = new Properties();

                String returnType = generateTypeText(myClass);
                properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType);
                properties.setProperty(
                    FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                    PsiTypesUtil.getDefaultValueOfType(JavaPsiFacade.getElementFactory(project).createType(myClass))
                );
                properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "");
                properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, myClass.getQualifiedName());
                properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, myClass.getName());
                properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, myMethodName);

                try {
                    String bodyText = StringUtil.replace(template.getText(properties), ";", "");
                    GrCodeBlock newBody =
                        GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n" + bodyText + "\n");

                    GrMethod method = GroovyPsiElementFactory.getInstance(project)
                        .createMethodFromText("", myMethodName, returnType, ArrayUtil.EMPTY_STRING_ARRAY, myClass);
                    method.setBlock(newBody);
                    myClass.add(method);
                }
                catch (IOException e) {
                    LOG.error(e);
                }
            }
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return GroovyInspectionLocalize.addMethod(myMethodName, myClass.getName());
        }
    }

    @RequiredReadAction
    private static String generateTypeText(GrTypeDefinition aClass) {
        StringBuilder returnType = new StringBuilder(aClass.getName());
        PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        if (typeParameters.length > 0) {
            returnType.append('<');
            for (PsiTypeParameter typeParameter : typeParameters) {
                returnType.append(typeParameter.getName()).append(", ");
            }
            returnType.replace(returnType.length() - 2, returnType.length(), ">");
        }
        return returnType.toString();
    }

    private static class AddClassToExtends extends GroovyFix {
        private GrTypeDefinition myPsiClass;
        private String myInterfaceName;

        public AddClassToExtends(GrTypeDefinition psiClass, String interfaceName) {
            myPsiClass = psiClass;
            myInterfaceName = interfaceName;
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrReferenceList list;
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

            PsiClass comparable =
                JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, myPsiClass.getResolveScope());
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            boolean addTypeParam = false;
            if (comparable != null) {
                PsiTypeParameter[] typeParameters = comparable.getTypeParameters();
                if (typeParameters.length == 1) {
                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                    PsiTypeParameter[] classParams = myPsiClass.getTypeParameters();
                    PsiSubstitutor innerSubstitutor = PsiSubstitutor.EMPTY;
                    for (PsiTypeParameter classParam : classParams) {
                        innerSubstitutor = innerSubstitutor.put(classParam, elementFactory.createType(classParam));
                    }
                    substitutor = substitutor.put(typeParameters[0], elementFactory.createType(myPsiClass, innerSubstitutor));
                    addTypeParam = true;
                }
            }

            if (!InheritanceUtil.isInheritor(myPsiClass, CommonClassNames.JAVA_LANG_COMPARABLE)) {
                if (myPsiClass.isInterface()) {
                    list = myPsiClass.getExtendsClause();
                    if (list == null) {
                        list = factory.createExtendsClause();

                        PsiElement anchor = myPsiClass.getImplementsClause();
                        if (anchor == null) {
                            anchor = myPsiClass.getBody();
                        }
                        if (anchor == null) {
                            return;
                        }
                        list = (GrReferenceList) myPsiClass.addBefore(list, anchor);
                        myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", anchor.getNode());
                        myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
                    }
                }
                else {
                    list = myPsiClass.getImplementsClause();
                    if (list == null) {
                        list = factory.createImplementsClause();
                        PsiElement anchor = myPsiClass.getBody();
                        if (anchor == null) {
                            return;
                        }
                        list = (GrReferenceList) myPsiClass.addBefore(list, anchor);
                        myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
                        myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", anchor.getNode());
                    }
                }


                GrCodeReferenceElement _ref =
                    factory.createReferenceElementFromText(myInterfaceName + (addTypeParam ? "<" + generateTypeText(myPsiClass) + ">" : ""));
                GrCodeReferenceElement ref = (GrCodeReferenceElement) list.add(_ref);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
            }
            if (comparable != null && !myPsiClass.isInterface()) {
                PsiMethod baseMethod = comparable.getMethods()[0];
                OverrideImplementUtil.overrideOrImplement(myPsiClass, baseMethod);
            }
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return GroovyInspectionLocalize.implementClass(myInterfaceName);
        }
    }
}
