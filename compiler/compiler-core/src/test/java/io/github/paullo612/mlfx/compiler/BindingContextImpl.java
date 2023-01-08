/*
 * Copyright 2023 Paullo612
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.paullo612.mlfx.compiler;

import io.github.paullo612.mlfx.api.CompiledLoadException;
import io.github.paullo612.mlfx.api.ObservableListenerHelper;
import io.github.paullo612.mlfx.compiler.elements.ElementUtils;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.github.paullo612.mlfx.expression.BindingContext;
import io.github.paullo612.mlfx.expression.test.ExpressionIntroductionDelegate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PrimitiveElement;
import jakarta.inject.Singleton;
import javafx.beans.property.Property;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class BindingContextImpl implements BindingContext {

    private static final String EXPRESSION_VALUE_FIELD_NAME = "expressionValue";
    private static final String OBSERVABLE_VALUE_N = "javafx/beans/value/ObservableValue";

    private final ClassElement targetType;
    private final String internalClassName;
    private final Map<String, Loadable> scope;
    private final Function<Class<?>, ClassElement> classElementRetriever;

    private final ClassWriter delegateWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            // NB: This is hack for tests. There is no Expression class on classpath when we're compiling in memory.
            if (expressionRenderer != null) {
                if (type1.equals(expressionRenderer.getInternalClassName()) && OBSERVABLE_VALUE_N.equals(type2)) {
                    return type2;
                }

                if (OBSERVABLE_VALUE_N.equals(type1) && type2.equals(expressionRenderer.getInternalClassName())) {
                    return type1;
                }
            }
            return super.getCommonSuperClass(type1, type2);
        }
    };

    private BindingExpressionRendererImpl expressionRenderer;

    private static String getClassElementName(ClassElement element) {
        if (!element.isArray()) {
            String name = element.getName();

            if (element.getBoundGenericTypes().isEmpty()) {
                return name;
            }

            return name + "<" + element.getBoundGenericTypes().stream()
                    .map(Element::getName)
                    .collect(Collectors.joining(", ")) + ">";
        }

        if (element.isPrimitive()) {
            return RenderUtils.type(element).getDescriptor();
        }

        return "[".repeat(element.getArrayDimensions()) + "L" + element.getName() + ";";
    }

    private static String getMethodDescription(MethodElement element) {
        String typeString = getClassElementName(element.getReturnType());
        String args = Arrays.stream(element.getParameters())
                .map(arg -> getClassElementName(arg.getType()) + " " + arg.getName())
                .collect(Collectors.joining(","));

        return typeString + " " + element.getName() + "(" + args + ")";
    }

    BindingContextImpl(MethodElement element, Function<Class<?>, ClassElement> classElementRetriever) {
        ClassElement classElement = element.getDeclaringType();
        ClassElement owningClassElement = element.getOwningType();

        this.targetType = ClassElement.of(classElement.getName() + "ExpressionDelegate");
        this.internalClassName = RenderUtils.type(targetType).getInternalName();

        this.scope = owningClassElement.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyAccessible()).stream()
                .collect(Collectors.toMap(Element::getName, f -> new Loadable() {
                    @Override
                    public ClassElement getClassElement() {
                        return f.getType();
                    }

                    @Override
                    public RenderCommand load() {
                        return methodVisitor -> {
                            Type owningClassObject = RenderUtils.type(owningClassElement);

                            methodVisitor.loadArg(0);
                            methodVisitor.checkCast(owningClassObject);
                            methodVisitor.getField(
                                    owningClassObject,
                                    f.getName(),
                                    RenderUtils.type(getClassElement())
                            );
                        };
                    }
                }));
        this.classElementRetriever = classElementRetriever;

        startDelegateClass(element);
    }

    private void startDelegateClass(MethodElement methodElement) {
        Type parentInterface = Type.getType(ExpressionIntroductionDelegate.class);

        delegateWriter.visitAnnotation(Type.getType(Singleton.class).getDescriptor(), true);

        // Class
        delegateWriter.visit(
                Opcodes.V11,
                Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                internalClassName,
                null,
                RenderUtils.OBJECT_N,
                new String[] { parentInterface.getInternalName() }
        );

        // Default constructor
        MethodVisitor defaultConstructor = delegateWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                RenderUtils.CONSTRUCTOR_N,
                "()V",
                null,
                null
        );

        // ALOAD 0 (this)
        defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        // INVOKESPECIAL java/lang/Object.<init> ()V
        defaultConstructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                RenderUtils.OBJECT_N,
                RenderUtils.CONSTRUCTOR_N,
                "()V",
                false
        );
        // RETURN
        defaultConstructor.visitInsn(Opcodes.RETURN);
        // MAXSTACK = 1 (this)
        // MAXLOCALS = 1 (this)
        defaultConstructor.visitMaxs(1, 1);
        defaultConstructor.visitEnd();

        // getMethodDeclaringType method
        MethodVisitor getMethodDeclaringType = delegateWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "getMethodDeclaringType",
                "()Ljava/lang/Class;",
                null,
                null
        );

        getMethodDeclaringType.visitLdcInsn(RenderUtils.type(methodElement.getDeclaringType()));
        // ARETURN
        getMethodDeclaringType.visitInsn(Opcodes.ARETURN);
        // MAXSTACK = 1 (classElement.class)
        // MAXLOCALS = 1 (this)
        getMethodDeclaringType.visitMaxs(1, 1);
        getMethodDeclaringType.visitEnd();

        // getMethodDescription method
        MethodVisitor getMethodDescription = delegateWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "getMethodDescription",
                "()" + RenderUtils.STRING_D,
                null,
                null
        );
        getMethodDescription.visitLdcInsn(getMethodDescription(methodElement));
        // ARETURN
        getMethodDescription.visitInsn(Opcodes.ARETURN);
        // MAXSTACK = 1 (description)
        // MAXLOCALS = 1 (this)
        getMethodDescription.visitMaxs(1, 1);
        getMethodDescription.visitEnd();
    }

    @Override
    public BindingExpressionRenderer createExpressionRenderer(
            String expression,
            Class<? extends Property> parentClass,
            ClassElement genericType) {
        if (this.expressionRenderer != null) {
            throw new UnsupportedOperationException();
        }

        this.expressionRenderer = new BindingExpressionRendererImpl(
                targetType,
                "Expression",
                expression,
                parentClass,
                genericType,
                getClassElement(ObservableListenerHelper.class)
        );

        String innerInternalClassName = this.expressionRenderer.getInternalClassName();
        delegateWriter.visitNestMember(innerInternalClassName);
        delegateWriter.visitInnerClass(
                innerInternalClassName,
                internalClassName,
                this.expressionRenderer.getClassName(),
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
        );

        return this.expressionRenderer;
    }

    void renderExpression(ClassElement targetType, RenderCommand renderCommand) {
        Type fieldType = null;
        ClassElement boxedClass = null;

        if (ElementUtils.isPrimitive(targetType)) {
            for (Map.Entry<Class<?>, PrimitiveElement> entry : RenderUtils.BOXED_PRIMITIVES.entrySet()) {
                if (targetType.isAssignable(entry.getValue())) {
                    boxedClass = getClassElement(entry.getKey());
                    fieldType = Type.getType(entry.getKey());
                    break;
                }
            }

            assert fieldType != null;
        } else {
            fieldType = RenderUtils.type(targetType);
        }

        Type objectType = Type.getObjectType(internalClassName);

        // expressionValue field
        delegateWriter.visitField(
                Opcodes.ACC_PRIVATE,
                EXPRESSION_VALUE_FIELD_NAME,
                fieldType.getDescriptor(),
                null,
                null
        );

        String loadMethodName = "getExpressionValue";
        String loadMethodDescriptor = "(" + RenderUtils.OBJECT_D + ")" + RenderUtils.OBJECT_D;

        // getExpressionValue method
        GeneratorAdapter methodVisitor = new GeneratorAdapter(
                delegateWriter.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        loadMethodName,
                        loadMethodDescriptor,
                        null,
                        new String[] { Type.getType(CompiledLoadException.class).getInternalName() }
                ),
                Opcodes.ACC_PUBLIC,
                loadMethodName,
                loadMethodDescriptor
        );
        methodVisitor.visitCode();

        // if (this.expressionValue == null) {
        //     this.expressionValue == ... renderCommand ...;
        // }
        //
        // return this.expressionValue;
        methodVisitor.loadThis();
        methodVisitor.getField(objectType, EXPRESSION_VALUE_FIELD_NAME, fieldType);
        // DUP, as field is likely to be cached.
        methodVisitor.dup();

        Label out = methodVisitor.newLabel();
        methodVisitor.ifNonNull(out);
        // Not interested in DUPed expressionValue. It is null anyway.
        methodVisitor.pop();

        renderCommand.render(methodVisitor);
        if (boxedClass != null) {
            RenderUtils.coerceOrThrow(this, targetType, boxedClass).render(methodVisitor);
        }

        // We'll return DUPed value.
        methodVisitor.dup();

        // Store expressionValue to class field.
        methodVisitor.loadThis();
        methodVisitor.swap();
        methodVisitor.putField(objectType, EXPRESSION_VALUE_FIELD_NAME, fieldType);

        methodVisitor.mark(out);
        methodVisitor.returnValue();
        // MAXSTACK = 2 + ?
        // MAXLOCALS = 2 (this, Object)
        methodVisitor.visitMaxs(0, 0);

        methodVisitor.visitEnd();
    }

    @Override
    public RuntimeException compileError(String message) {
        return new CompileErrorException(message);
    }

    @Override
    public ClassElement getClassElement(Class<?> type) {
        return classElementRetriever.apply(type);
    }

    @Override
    public Map<String, Loadable> getScope() {
        return scope;
    }

    String getInternalClassName() {
        return internalClassName;
    }

    BindingExpressionRendererImpl getExpressionRenderer() {
        return expressionRenderer;
    }

    byte[] dispose() {
        delegateWriter.visitEnd();

        return delegateWriter.toByteArray();
    }
}
