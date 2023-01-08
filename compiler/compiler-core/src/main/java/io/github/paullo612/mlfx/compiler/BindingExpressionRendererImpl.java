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

import io.github.paullo612.mlfx.api.ObservableListenerHelper;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.github.paullo612.mlfx.expression.BindingContext;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.inject.ast.ClassElement;
import javafx.beans.property.Property;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

class BindingExpressionRendererImpl implements BindingContext.BindingExpressionRenderer {

    private static final String GET_BEAN_METHOD_N = "getBean";
    private static final String GET_BEAN_METHOD_D = "()" + RenderUtils.OBJECT_D;

    private static final String GET_NAME_METHOD_N = "getName";
    private static final String GET_NAME_METHOD_D = "()" + RenderUtils.STRING_D;

    private static final String UPDATE_METHOD_N = "update";
    private static final String UPDATE_METHOD_D = "(I)V";

    private static final String LISTENER_HELPER_FIELD_N = "listenerHelper";

    static final String ARG_CAPTURE_NAME = "arg";
    static final String STORE_NAME = "store";

    private static class FlagSetImpl implements CompilerContext.FlagSet {

        private static final String FLAGS_FIELD_NAME = "flags";

        private static class FlagImpl implements Flag {
            private final FlagSetImpl flagSet;
            private final int index;

            FlagImpl(FlagSetImpl flagSet, int index) {
                this.flagSet = flagSet;
                this.index = index;
            }

            @Override
            public CompilerContext.RenderCommand check() {
                return methodVisitor -> flagSet.doCheck(methodVisitor, index);
            }

            @Override
            public CompilerContext.RenderCommand set() {
                return methodVisitor -> flagSet.doSet(methodVisitor, index);
            }

            @Override
            public ExpressionContext.RenderCommand clear() {
                return methodVisitor -> flagSet.doClear(methodVisitor, index);
            }
        }

        private final Type objectType;
        private int counter;

        FlagSetImpl(Type objectType) {
            this.objectType = objectType;
        }

        @Override
        public Flag createFlag() {
            return new FlagImpl(this, counter++);
        }

        private void doCheck(GeneratorAdapter methodVisitor, int index) {
            methodVisitor.loadThis();

            if (counter <= Long.SIZE) {
                Type type = Type.getType(counter <= Integer.SIZE ? int.class : long.class);

                methodVisitor.getField(objectType, FLAGS_FIELD_NAME, type);
                if (counter <= Integer.SIZE) {
                    methodVisitor.push(1 << index);
                } else {
                    methodVisitor.push(1L << index);
                }
                methodVisitor.math(GeneratorAdapter.AND, type);

                Label zeroLabel = methodVisitor.newLabel();
                Label out = methodVisitor.newLabel();
                methodVisitor.ifZCmp(GeneratorAdapter.EQ, zeroLabel);
                methodVisitor.push(true);
                methodVisitor.goTo(out);
                methodVisitor.mark(zeroLabel);
                methodVisitor.push(false);
                methodVisitor.mark(out);
            } else {
                Type type = Type.getType(BitSet.class);
                methodVisitor.getField(objectType, FLAGS_FIELD_NAME, type);
                methodVisitor.push(index);
                methodVisitor.invokeVirtual(type, new Method("get", "(I)Z"));
            }
        }

        private void doSet(GeneratorAdapter methodVisitor, int index) {
            methodVisitor.loadThis();

            if (counter <= Long.SIZE) {
                methodVisitor.loadThis();
                Type type = Type.getType(counter <= Integer.SIZE ? int.class : long.class);

                methodVisitor.getField(objectType, FLAGS_FIELD_NAME, type);
                if (counter <= Integer.SIZE) {
                    methodVisitor.push(1 << index);
                } else {
                    methodVisitor.push(1L << index);
                }
                methodVisitor.math(GeneratorAdapter.OR, type);
                methodVisitor.putField(objectType, FLAGS_FIELD_NAME, type);
            } else {
                Type type = Type.getType(BitSet.class);
                methodVisitor.getField(objectType, FLAGS_FIELD_NAME, type);
                methodVisitor.push(index);
                methodVisitor.push(true);
                methodVisitor.invokeVirtual(type, new Method("set", "(IZ)V"));
            }
        }

        private void doClear(GeneratorAdapter methodVisitor, int index) {
            methodVisitor.loadThis();

            if (counter <= Long.SIZE) {
                methodVisitor.loadThis();
                Type type = Type.getType(counter <= Integer.SIZE ? int.class : long.class);

                methodVisitor.getField(objectType, FLAGS_FIELD_NAME, type);
                if (counter <= Integer.SIZE) {
                    methodVisitor.push(~(1 << index));
                } else {
                    methodVisitor.push(~(1L << index));
                }
                methodVisitor.math(GeneratorAdapter.AND, type);
                methodVisitor.putField(objectType, FLAGS_FIELD_NAME, type);
            } else {
                Type type = Type.getType(BitSet.class);
                methodVisitor.getField(objectType, FLAGS_FIELD_NAME, type);
                methodVisitor.push(index);
                methodVisitor.push(false);
                methodVisitor.invokeVirtual(type, new Method("set", "(IZ)V"));
            }
        }

        void render(ClassVisitor classVisitor, MethodVisitor constructor) {
            int accessFlags = Opcodes.ACC_PRIVATE;

            Type type;

            if (counter <= Integer.SIZE) {
                type = Type.getType(int.class);
            } else if (counter <= Long.SIZE) {
                type = Type.getType(long.class);
            } else {
                type = Type.getType(BitSet.class);
                accessFlags |= Opcodes.ACC_FINAL;

                // Construct new BitSet
                constructor.visitVarInsn(Opcodes.ALOAD, 0);
                constructor.visitTypeInsn(Opcodes.NEW, type.getInternalName());
                constructor.visitInsn(Opcodes.DUP);
                constructor.visitMethodInsn(
                        Opcodes.INVOKESPECIAL,
                        type.getInternalName(),
                        RenderUtils.CONSTRUCTOR_N,
                        "()V",
                        false
                );
                constructor.visitFieldInsn(
                        Opcodes.PUTFIELD,
                        objectType.getInternalName(),
                        FLAGS_FIELD_NAME,
                        type.getDescriptor()
                );
            }

            // Add field to class.
            classVisitor.visitField(
                    accessFlags,
                    FLAGS_FIELD_NAME,
                    type.getDescriptor(),
                    null,
                    null
            );
        }
    }

    private static class FieldReference implements CompilerContext.Storable {

        private final ClassElement classElement;
        private final Type parent;
        private final String fieldName;

        FieldReference(ClassElement classElement, Type parent, String fieldName) {
            this.classElement = classElement;
            this.parent = parent;
            this.fieldName = fieldName;
        }

        @Override
        public ClassElement getClassElement() {
            return classElement;
        }

        @Override
        public CompilerContext.RenderCommand load() {
            return methodVisitor -> {
                methodVisitor.loadThis();
                methodVisitor.getField(
                        parent,
                        fieldName,
                        RenderUtils.type(classElement)
                );
            };
        }

        void store(MethodVisitor methodVisitor) {
            methodVisitor.visitFieldInsn(
                    Opcodes.PUTFIELD,
                    parent.getInternalName(),
                    fieldName,
                    RenderUtils.type(classElement).getDescriptor()
            );
        }

        void store(GeneratorAdapter methodVisitor) {
            store(methodVisitor.getDelegate());
        }

        @Override
        public CompilerContext.RenderCommand store(CompilerContext.RenderCommand command) {
            return methodVisitor -> {
                methodVisitor.loadThis();
                command.render(methodVisitor);
                store(methodVisitor);
            };
        }
    }

    private final String className;
    private final String internalClassName;
    private final Type parentType;

    private final ClassWriter expressionWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    private final FieldReference listenerHelperField;
    private final GeneratorAdapter updateMethodVisitor;
    private final Label updateMethodCodeStart;

    // NB: We could use identity hash map here, but this would make results non-reproducible, as IdentityHashMap does
    //  not maintain insertion order.
    private final Map<CompilerContext.Loadable, FieldReference> captures = new LinkedHashMap<>();
    private final FlagSetImpl flagSet;
    private int storeCounter;

    BindingExpressionRendererImpl(
            ClassElement outerClass,
            String className,
            String expression,
            Class<? extends Property> parentClass,
            ClassElement genericType,
            ClassElement listenerHelperClassElement) {
        String outerInternalClassName = RenderUtils.type(outerClass).getInternalName();

        this.className = className;
        this.internalClassName = outerInternalClassName + "$" + className;
        this.parentType = Type.getType(parentClass);

        Type valueUpdaterObject = Type.getType(ObservableListenerHelper.ValueUpdater.class);

        String signature;

        if (genericType != null) {
            signature = "L" + parentType.getInternalName() + "<" + RenderUtils.type(genericType).getDescriptor() + ">;";
        } else {
            signature = parentType.getDescriptor();
        }

        signature += valueUpdaterObject.getDescriptor();

        // Class
        this.expressionWriter.visit(
                Opcodes.V11,
                Opcodes.ACC_SUPER | Opcodes.ACC_FINAL,
                internalClassName,
                signature,
                parentType.getInternalName(),
                new String[] { valueUpdaterObject.getInternalName() }
        );

        expressionWriter.visitNestHost(outerInternalClassName);
        expressionWriter.visitInnerClass(
                internalClassName,
                outerInternalClassName,
                className,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
        );

        // visitorHelper field
        Type visitorHelperObject = RenderUtils.type(listenerHelperClassElement);

        expressionWriter.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                LISTENER_HELPER_FIELD_N,
                visitorHelperObject.getDescriptor(),
                null,
                null
        );

        Type objectType = Type.getObjectType(internalClassName);
        this.listenerHelperField = new FieldReference(
                listenerHelperClassElement,
                objectType,
                LISTENER_HELPER_FIELD_N
        );
        this.flagSet = new FlagSetImpl(objectType);


        // NB: We'll generate constructor later, in dispose method, when all captures will be known.

        // getBean method
        MethodVisitor getBeanMethodVisitor = expressionWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                GET_BEAN_METHOD_N,
                GET_BEAN_METHOD_D,
                null,
                null
        );
        getBeanMethodVisitor.visitCode();
        getBeanMethodVisitor.visitInsn(Opcodes.ACONST_NULL);
        getBeanMethodVisitor.visitInsn(Opcodes.ARETURN);

        // MAXSTACK = 1 (null)
        // MAXLOCALS = 1 (this)
        getBeanMethodVisitor.visitMaxs(1, 1);
        getBeanMethodVisitor.visitEnd();

        // getName method
        MethodVisitor getNameMethodVisitor = expressionWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                GET_NAME_METHOD_N,
                GET_NAME_METHOD_D,
                null,
                null
        );
        getNameMethodVisitor.visitCode();
        getNameMethodVisitor.visitLdcInsn(expression);
        getNameMethodVisitor.visitInsn(Opcodes.ARETURN);

        // MAXSTACK = 1 (name)
        // MAXLOCALS = 1 (this)
        getNameMethodVisitor.visitMaxs(1, 1);
        getNameMethodVisitor.visitEnd();

        // update method
        this.updateMethodVisitor = new GeneratorAdapter(
                expressionWriter.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        UPDATE_METHOD_N,
                        UPDATE_METHOD_D,
                        null,
                        null
                ),
                Opcodes.ACC_PUBLIC,
                UPDATE_METHOD_N,
                UPDATE_METHOD_D
        );

        updateMethodVisitor.visitCode();

        // listenerHelper.lockListeners();
        listenerHelperField.load().render(updateMethodVisitor);
        updateMethodVisitor.invokeInterface(
                visitorHelperObject,
                new Method("lockListeners", "()V")
        );

        updateMethodCodeStart = updateMethodVisitor.mark();
    }

    String getInternalClassName() {
        return internalClassName;
    }

    String getClassName() {
        return className;
    }

    @Override
    public void render(CompilerContext.RenderCommand command) {
        command.render(updateMethodVisitor);
    }

    @Override
    public CompilerContext.Loadable capture(CompilerContext.Loadable loadable) {
        FieldReference result = captures.get(loadable);

        if (result == null) {
            ClassElement loadableClassElement = loadable.getClassElement();
            String fieldName = ARG_CAPTURE_NAME + captures.size();

            // NB: All captured fields are final. There is no sense in writing out generic signatures, as everything
            //  we'll capture will have erased generic types.
            expressionWriter.visitField(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    fieldName,
                    RenderUtils.type(loadableClassElement).getDescriptor(),
                    null,
                    null
            );

            result = new FieldReference(loadableClassElement, Type.getObjectType(internalClassName), fieldName);
            captures.put(loadable, result);
        }

        return result;
    }

    @Override
    public CompilerContext.Storable store(ClassElement classElement) {
        String fieldName = STORE_NAME + (storeCounter++);

        expressionWriter.visitField(
                Opcodes.ACC_PRIVATE,
                fieldName,
                RenderUtils.type(classElement).getDescriptor(),
                null,
                null
        );

        return new FieldReference(classElement, Type.getObjectType(internalClassName), fieldName);
    }

    @Override
    public CompilerContext.RenderCommand newInstance() {
        return methodVisitor -> {
            Type objectType = Type.getObjectType(internalClassName);

            methodVisitor.newInstance(objectType);
            methodVisitor.dup();

            String constructorDescriptor = "(";

            for (CompilerContext.Loadable loadable : captures.keySet()) {
                loadable.load().render(methodVisitor);
                constructorDescriptor += RenderUtils.type(loadable.getClassElement());
            }

            constructorDescriptor += ")V";

            methodVisitor.invokeConstructor(objectType, new Method(RenderUtils.CONSTRUCTOR_N, constructorDescriptor));
        };
    }

    @Override
    public CompilerContext.Loadable getListenerHelper() {
        return listenerHelperField;
    }

    @Override
    public Flag createFlag() {
        return flagSet.createFlag();
    }

    private void generateConstructor() {
        StringBuilder constructorDescriptorBuilder = new StringBuilder("(");

        for (CompilerContext.Loadable value : captures.values()) {
            constructorDescriptorBuilder.append(RenderUtils.type(value.getClassElement()));
        }
        constructorDescriptorBuilder.append(")V");

        String constructorDescriptor = constructorDescriptorBuilder.toString();

        // Default constructor
        MethodVisitor defaultConstructor =
                expressionWriter.visitMethod(
                        0,
                        RenderUtils.CONSTRUCTOR_N,
                        constructorDescriptor,
                        null,
                        null
                );

        // ALOAD 0 (this)
        defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);

        // INVOKESPECIAL |parentType|.<init> (Ljava/lang/Object;Ljava/lang/String;)V
        defaultConstructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                parentType.getInternalName(),
                RenderUtils.CONSTRUCTOR_N,
                "()V",
                false
        );

        // Instantiate ObservableListenerHelper.
        Type listenerHelperObject = Type.getType(ObservableListenerHelper.class);

        // ALOAD 0 (this) (For store operation)
        defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);

        // ALOAD 0 (this) (For static call argument)
        defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        defaultConstructor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                listenerHelperObject.getInternalName(),
                "newInstance",
                "(" + Type.getType(ObservableListenerHelper.ValueUpdater.class).getDescriptor() + ")"
                        + listenerHelperObject.getDescriptor(),
                true
        );

        listenerHelperField.store(defaultConstructor);

        // Initialize flags field.
        flagSet.render(expressionWriter, defaultConstructor);

        // Store captured values.
        int i = 0;
        for (FieldReference value : captures.values()) {
            // Load this.
            defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);
            // Load argument.
            defaultConstructor.visitVarInsn(Opcodes.ALOAD, i + 1);

            value.store(defaultConstructor);
            ++i;
        }

        // ALOAD 0 (this)
        defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        // ICONST_0 (0)
        defaultConstructor.visitInsn(Opcodes.ICONST_0);

        defaultConstructor.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                Type.getType(ObservableListenerHelper.ValueUpdater.class).getInternalName(),
                "update",
                "(I)V",
                true
        );

        // RETURN
        defaultConstructor.visitInsn(Opcodes.RETURN);
        // MAXSTACK = 3 (this, bean, name)
        // MAXLOCALS = 1 + ... (this, ...)
        defaultConstructor.visitMaxs(3, 1 + captures.size());
        defaultConstructor.visitEnd();
    }

    private void unlockListeners() {
        listenerHelperField.load().render(updateMethodVisitor);
        updateMethodVisitor.invokeInterface(
                Type.getType(ObservableListenerHelper.class),
                new Method("unlockListeners", "()V")
        );
    }

    byte[] dispose() {
        // try {
        //     ...
        // } finally {
        //     listenerHelper.unlockListeners();
        // }
        Label out = updateMethodVisitor.newLabel();
        Label codeEnd = updateMethodVisitor.mark();
        unlockListeners();
        updateMethodVisitor.goTo(out);

        updateMethodVisitor.catchException(updateMethodCodeStart, codeEnd, null);
        updateMethodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
        unlockListeners();
        updateMethodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
        updateMethodVisitor.visitInsn(Opcodes.ATHROW);

        updateMethodVisitor.mark(out);
        updateMethodVisitor.visitInsn(Opcodes.RETURN);
        updateMethodVisitor.endMethod();

        // It's time to generate constructor.
        generateConstructor();

        expressionWriter.visitEnd();

        return expressionWriter.toByteArray();
    }
}
