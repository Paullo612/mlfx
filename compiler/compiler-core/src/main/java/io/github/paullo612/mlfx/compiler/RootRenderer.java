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

import io.github.paullo612.mlfx.api.CompiledFXMLLoader;
import io.github.paullo612.mlfx.api.CompiledLoadException;
import io.github.paullo612.mlfx.api.ControllerAccessor;
import io.github.paullo612.mlfx.api.ControllerAccessorFactory;
import io.github.paullo612.mlfx.api.GeneratedByMLFX;
import io.github.paullo612.mlfx.api.Result;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.micronaut.inject.ast.ClassElement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

class RootRenderer implements CompilerContext.Renderer {

    private static final String CONSTRUCTOR_DESCRIPTOR = "()V";
    private static final String LOAD_METHOD_NAME = "doLoad";

    static final String RESOURCE_BUNDLE_D = "Ljava/util/ResourceBundle;";

    private static final String OPTIONAL_N = "java/util/Optional";
    private static final String OPTIONAL_D = "L" + OPTIONAL_N + ";";

    private static final String GET_ABI_VERSION_METHOD_NAME = "getABIVersion";
    private static final String GET_URI_METHOD_NAME = "getURI";
    private static final String REQUIRES_RESOURCE_BUNDLE_METHOD_NAME = "requiresResourceBundle";
    private static final String REQUIRES_EXTERNAL_CONTROLLER_METHOD_NAME = "requiresExternalController";
    private static final String GET_ROOT_INSTANCE_CLASS_METHOD_NAME = "getRootInstanceClass";
    private static final String GET_CONTROLLER_METHOD_NAME = "getControllerClass";

    private static final String CREATE_RESULT_METHOD_NAME = "createResult";

    private static final int CONTROLLER_ACCESSOR_FACTORY_LOCAL_INDEX = 1;
    private static final int RESOURCE_BUNDLE_LOCAL_INDEX = 2;
    private static final int ROOT_INSTANCE_LOCAL_INDEX = 3;
    private static final int ACCESSOR_LOCAL_INDEX = 4;
    private static final int CONTROLLER_LOCAL_INDEX = 5;

    static final int LAST_LOCAL_INDEX = CONTROLLER_LOCAL_INDEX;

    private static void loadLocation(String fxmlFileName, GeneratorAdapter methodVisitor) {
        String exceptionMessage = "Cannot find resource \"./" + fxmlFileName + "\" on classpath.";

        // URL resource = getClass().getResource(fxmlFileName);
        // if (resource == null) {
        //    throw new CompiledLoadException(exceptionMessage);
        // }
        methodVisitor.loadThis();

        Type classType = Type.getType(Class.class);
        Type urlType = Type.getType(URL.class);

        methodVisitor.invokeVirtual(
                Type.getType(Object.class),
                new Method("getClass", "()" + classType.getDescriptor())
        );
        methodVisitor.push(fxmlFileName);
        methodVisitor.invokeVirtual(
                classType,
                new Method("getResource", "(" + RenderUtils.STRING_D + ")" + urlType.getDescriptor())
        );

        Label label = methodVisitor.newLabel();
        methodVisitor.dup();
        methodVisitor.ifNonNull(label);
        methodVisitor.throwException(Type.getType(CompiledLoadException.class), exceptionMessage);
        methodVisitor.mark(label);
    }

    static void loadControllerAccessorFactory(GeneratorAdapter methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, CONTROLLER_ACCESSOR_FACTORY_LOCAL_INDEX);
    }

    static void loadNonRequiredResourceBundle(GeneratorAdapter methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, RESOURCE_BUNDLE_LOCAL_INDEX);
    }

    static void loadRootInstance(GeneratorAdapter methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, ROOT_INSTANCE_LOCAL_INDEX);
    }

    static void loadAccessor(GeneratorAdapter methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, ACCESSOR_LOCAL_INDEX);
    }

    static void loadController(GeneratorAdapter methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, CONTROLLER_LOCAL_INDEX);
    }

    private final ClassWriter loaderWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    private final String fxmlFileName;

    private GeneratorAdapter loadMethodVisitor;
    private Label methodStartLabel;
    private ClassElement rootClassElement;
    private ClassElement controllerClassElement;
    private Type rootType;
    private Type controllerType;
    private boolean hasFxRoot;
    private boolean hasController;
    private boolean requiresExternalController;

    private String internalClassName;

    private boolean requiresResourceBundle;

    RootRenderer(String fxmlFileName) {
        this.fxmlFileName = fxmlFileName;
    }

    void initialize(
            ClassElement targetType,
            ClassElement rootClassElement,
            ClassElement controllerClassElement,
            boolean hasFxRoot,
            boolean hasController,
            boolean requiresExternalController) {
        this.rootClassElement = rootClassElement;
        this.controllerClassElement = controllerClassElement;
        this.rootType = RenderUtils.type(rootClassElement);
        this.controllerType = RenderUtils.type(controllerClassElement);
        this.hasFxRoot = hasFxRoot;
        this.hasController = hasController;
        this.requiresExternalController = requiresExternalController;

        this.internalClassName = RenderUtils.type(targetType).getInternalName();

        startLoaderClass();

        this.loadMethodVisitor = startLoadMethod();
        this.methodStartLabel = loadMethodVisitor.mark();
    }

    private void startLoaderClass() {
        Type parentClass = Type.getType(CompiledFXMLLoader.class);

        String signature = "L" + parentClass.getInternalName() + "<" + rootType.getDescriptor()
                + controllerType.getDescriptor() + ">;";

        // Class
        loaderWriter.visit(
                Opcodes.V11,
                Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                internalClassName,
                signature,
                parentClass.getInternalName(),
                null
        );

        // Mark as generated by us.
        loaderWriter.visitAnnotation(Type.getType(GeneratedByMLFX.class).getDescriptor(), false);

        // Default constructor
        MethodVisitor defaultConstructor = loaderWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                RenderUtils.CONSTRUCTOR_N,
                CONSTRUCTOR_DESCRIPTOR,
                null,
                null
        );

        // ALOAD 0 (this)
        defaultConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        // INVOKESPECIAL java/lang/Object.<init> ()V
        defaultConstructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                parentClass.getInternalName(),
                RenderUtils.CONSTRUCTOR_N,
                CONSTRUCTOR_DESCRIPTOR,
                false
        );
        // RETURN
        defaultConstructor.visitInsn(Opcodes.RETURN);
        // MAXSTACK = 1 (this)
        // MAXLOCALS = 1 (this)
        defaultConstructor.visitMaxs(1, 1);
        defaultConstructor.visitEnd();
    }

    private GeneratorAdapter startLoadMethod() {
        String descriptorPart = "(" + Type.getType(ControllerAccessorFactory.class) + RESOURCE_BUNDLE_D;
        Type resultType = Type.getType(Result.class);
        Type accessorType = Type.getType(ControllerAccessor.class);

        // Generate bridge method first.
        MethodVisitor bridgeMethod = loaderWriter.visitMethod(
                Opcodes.ACC_PROTECTED | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                LOAD_METHOD_NAME,
                descriptorPart + RenderUtils.OBJECT_D + accessorType.getDescriptor() + RenderUtils.OBJECT_D + ")"
                        + resultType.getDescriptor(),
                null,
                new String[] { Type.getType(CompiledLoadException.class).getInternalName() }
        );

        bridgeMethod.visitCode();
        // ALOAD 0 (this)
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, 0);
        // ALOAD 1 (ControllerAccessorFactory)
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, CONTROLLER_ACCESSOR_FACTORY_LOCAL_INDEX);
        // ALOAD 2 (ResourceBundle)
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, RESOURCE_BUNDLE_LOCAL_INDEX);
        // ALOAD 3 (|rootType|)
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, ROOT_INSTANCE_LOCAL_INDEX);
        bridgeMethod.visitTypeInsn(Opcodes.CHECKCAST, rootType.getInternalName());

        // ALOAD 4 (ControllerAccessor<|controllerType|>)
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, ACCESSOR_LOCAL_INDEX);

        // ALOAD 5 (|controllerType|)
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, CONTROLLER_LOCAL_INDEX);
        bridgeMethod.visitTypeInsn(Opcodes.CHECKCAST, controllerType.getInternalName());

        bridgeMethod.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                internalClassName,
                LOAD_METHOD_NAME,
                descriptorPart + rootType.getDescriptor() + accessorType.getDescriptor()
                        + controllerType.getDescriptor() + ")" + resultType.getDescriptor(),
                false
        );

        // ARETURN
        bridgeMethod.visitInsn(Opcodes.ARETURN);
        // MAXSTACK = 6
        // MAXLOCALS = 6
        bridgeMethod.visitMaxs(6, 6);

        String descriptor = descriptorPart + rootType.getDescriptor() + accessorType.getDescriptor() +
                controllerType.getDescriptor() + ")" + resultType.getDescriptor();
        String signature = descriptorPart + rootType.getDescriptor() + "L" + accessorType.getInternalName() + "<"
                + controllerType.getDescriptor() + ">;" + controllerType.getDescriptor() + ")L"
                + resultType.getInternalName() + "<" + rootType.getDescriptor() + controllerType.getDescriptor() + ">;";

        GeneratorAdapter adapter = new GeneratorAdapter(
                loaderWriter.visitMethod(
                        Opcodes.ACC_PROTECTED,
                        LOAD_METHOD_NAME,
                        descriptor,
                        signature,
                        new String[] { Type.getType(CompiledLoadException.class).getInternalName() }
                ),
                Opcodes.ACC_PROTECTED,
                LOAD_METHOD_NAME,
                descriptor
        );

        adapter.visitCode();
        return adapter;
    }

    boolean isInitialized() {
        return loadMethodVisitor != null;
    }

    private void checkInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("Not initialized or disposed.");
        }
    }

    void addInnerClass(String innerInternalClassName, String innerClassName) {
        loaderWriter.visitNestMember(innerInternalClassName);
        loaderWriter.visitInnerClass(
                innerInternalClassName,
                internalClassName,
                innerClassName,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
        );
    }

    ClassElement getRootClassElement() {
        checkInitialized();

        return rootClassElement;
    }

    public ClassElement getControllerClassElement() {
        checkInitialized();

        return controllerClassElement;
    }

    @Override
    public void render(CompilerContext.RenderCommand command) {
        checkInitialized();

        command.render(loadMethodVisitor);
    }

    void loadResourceBundle(GeneratorAdapter methodVisitor) {
        requiresResourceBundle = true;
        loadNonRequiredResourceBundle(methodVisitor);
    }

    void loadLocation(GeneratorAdapter loadMethodVisitor) {
        loadLocation(fxmlFileName, loadMethodVisitor);
    }

    private void renderGetABIVersionMethod() {
        MethodVisitor getABIVersionMethod = loaderWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                GET_ABI_VERSION_METHOD_NAME,
                "()I",
                null,
                null
        );
        getABIVersionMethod.visitCode();
        if (CompiledFXMLLoader.ABI_VERSION >= -1 && CompiledFXMLLoader.ABI_VERSION <= 5) {
            getABIVersionMethod.visitInsn(Opcodes.ICONST_0 + CompiledFXMLLoader.ABI_VERSION);
        } else if (CompiledFXMLLoader.ABI_VERSION >= Byte.MIN_VALUE
                && CompiledFXMLLoader.ABI_VERSION <= Byte.MAX_VALUE) {
            getABIVersionMethod.visitIntInsn(Opcodes.BIPUSH, CompiledFXMLLoader.ABI_VERSION);
        } else if (CompiledFXMLLoader.ABI_VERSION >= Short.MIN_VALUE
                && CompiledFXMLLoader.ABI_VERSION <= Short.MAX_VALUE) {
            getABIVersionMethod.visitIntInsn(Opcodes.SIPUSH, CompiledFXMLLoader.ABI_VERSION);
        } else {
            getABIVersionMethod.visitLdcInsn(CompiledFXMLLoader.ABI_VERSION);
        }
        getABIVersionMethod.visitInsn(Opcodes.IRETURN);

        // MAXSTACK = 1 (result)
        // MAXLOCALS = 1 (this)
        getABIVersionMethod.visitMaxs(1, 1);
        getABIVersionMethod.visitEnd();
    }

    private void renderGetURIMethod() {
        Type uriType = Type.getType(URI.class);
        String descriptor = "()" + uriType.getDescriptor();

        GeneratorAdapter getURIMethod = new GeneratorAdapter(
                loaderWriter.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        GET_URI_METHOD_NAME,
                        descriptor,
                        null,
                        null
                ),
                Opcodes.ACC_PUBLIC,
                GET_URI_METHOD_NAME,
                descriptor
        );

        getURIMethod.visitCode();

        // URL resource = getClass().getResource(classFileName);
        // if (resource == null) {
        //     throw new CompiledLoadException(exceptionMessage);
        // }
        // try {
        //     return resource.toURI();
        // } catch (URISyntaxException e) {
        //     throw new CompiledLoadException(e);
        // }

        Type urlType = Type.getType(URL.class);

        Label start = getURIMethod.mark();

        getURIMethod.newInstance(urlType);
        getURIMethod.dup();

        // Expands to
        // URL resource = getClass().getResource(fxmlFileName);
        // if (resource == null) {
        //     throw new CompiledLoadException(exceptionMessage);
        // }
        loadLocation(fxmlFileName, getURIMethod);

        getURIMethod.push(fxmlFileName);

        getURIMethod.invokeConstructor(
                urlType,
                new Method(RenderUtils.CONSTRUCTOR_N, "(" + urlType.getDescriptor() + RenderUtils.STRING_D + ")V")
        );

        getURIMethod.invokeVirtual(urlType, new Method("toURI", "()" + uriType.getDescriptor()));
        getURIMethod.returnValue();

        Label end = getURIMethod.mark();

        Type exceptionType = Type.getType(URISyntaxException.class);
        getURIMethod.catchException(start, end, exceptionType);

        // Local variable slot index does not matter here. We'll throw anyway.
        getURIMethod.storeLocal(LAST_LOCAL_INDEX + 1, exceptionType);
        Type loadExceptionType = Type.getType(CompiledLoadException.class);
        getURIMethod.newInstance(loadExceptionType);
        getURIMethod.dup();
        getURIMethod.loadLocal(LAST_LOCAL_INDEX + 1);
        getURIMethod.invokeConstructor(
                loadExceptionType,
                new Method(
                        RenderUtils.CONSTRUCTOR_N, "(" + Type.getType(Throwable.class).getDescriptor() + ")V"
                )
        );
        getURIMethod.throwException();

        // MAXSTACK = 3 (URL, URL, String)
        // MAXLOCALS = 1 (this)
        getURIMethod.visitMaxs(3, 1);
        getURIMethod.visitEnd();
    }

    private void renderRequiresExternalControllerMethod() {
        MethodVisitor requiresExternalControllerMethod = loaderWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                REQUIRES_EXTERNAL_CONTROLLER_METHOD_NAME,
                "()Z",
                null,
                null
        );
        requiresExternalControllerMethod.visitCode();
        requiresExternalControllerMethod.visitInsn(requiresExternalController ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        requiresExternalControllerMethod.visitInsn(Opcodes.IRETURN);

        // MAXSTACK = 1 (result)
        // MAXLOCALS = 1 (this)
        requiresExternalControllerMethod.visitMaxs(1, 1);
        requiresExternalControllerMethod.visitEnd();
    }

    private void renderRequiresResourceBundleMethod() {
        MethodVisitor requiresResourceBundleMethod = loaderWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                REQUIRES_RESOURCE_BUNDLE_METHOD_NAME,
                "()Z",
                null,
                null
        );
        requiresResourceBundleMethod.visitCode();
        requiresResourceBundleMethod.visitInsn(requiresResourceBundle ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        requiresResourceBundleMethod.visitInsn(Opcodes.IRETURN);

        // MAXSTACK = 1 (result)
        // MAXLOCALS = 1 (this)
        requiresResourceBundleMethod.visitMaxs(1, 1);
        requiresResourceBundleMethod.visitEnd();
    }

    private void renderGetRootInstanceClassMethod() {
        MethodVisitor getRootInstanceClassMethod = loaderWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                GET_ROOT_INSTANCE_CLASS_METHOD_NAME,
                "()" + OPTIONAL_D,
                "()L" + OPTIONAL_N + "<L" + Type.getType(Class.class).getInternalName() + "<"
                        + rootType.getDescriptor() + ">;>;",
                null
        );

        getRootInstanceClassMethod.visitCode();
        if (hasFxRoot) {
            getRootInstanceClassMethod.visitLdcInsn(rootType);
            getRootInstanceClassMethod.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    OPTIONAL_N,
                    "of",
                    "(" + RenderUtils.OBJECT_D + ")" + OPTIONAL_D,
                    false
            );
        } else {
            getRootInstanceClassMethod.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    OPTIONAL_N,
                    "empty",
                    "()" + OPTIONAL_D,
                    false
            );
        }
        getRootInstanceClassMethod.visitInsn(Opcodes.ARETURN);

        // MAXSTACK = 1 (result)
        // MAXLOCALS = 1 (this)
        getRootInstanceClassMethod.visitMaxs(1, 1);

        getRootInstanceClassMethod.visitEnd();
    }

    private void renderGetControllerClassMethod() {
        MethodVisitor getControllerClassMethod = loaderWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                GET_CONTROLLER_METHOD_NAME,
                "()" + OPTIONAL_D,
                "()L" + OPTIONAL_N + "<L" + Type.getType(Class.class).getInternalName() + "<"
                        + controllerType.getDescriptor() + ">;>;",
                null
        );

        getControllerClassMethod.visitCode();
        if (hasController) {
            getControllerClassMethod.visitLdcInsn(controllerType);
            getControllerClassMethod.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    OPTIONAL_N,
                    "of",
                    "(" + RenderUtils.OBJECT_D + ")" + OPTIONAL_D,
                    false
            );
        } else {
            getControllerClassMethod.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    OPTIONAL_N,
                    "empty",
                    "()" + OPTIONAL_D,
                    false
            );
        }
        getControllerClassMethod.visitInsn(Opcodes.ARETURN);

        // MAXSTACK = 1 (result)
        // MAXLOCALS = 1 (this)
        getControllerClassMethod.visitMaxs(1, 1);
        getControllerClassMethod.visitEnd();
    }

    byte[] dispose() {
        loadMethodVisitor.loadThis();

        if (hasFxRoot) {
            loadRootInstance(loadMethodVisitor);
        } else {
            loadMethodVisitor.loadLocal(LAST_LOCAL_INDEX + 1);
        }
        loadController(loadMethodVisitor);
        loadMethodVisitor.invokeVirtual(
                Type.getType(CompiledFXMLLoader.class),
                new Method(
                        CREATE_RESULT_METHOD_NAME,
                        "(" + RenderUtils.OBJECT_D + RenderUtils.OBJECT_D + ")"
                                + Type.getType(Result.class).getDescriptor()
                )
        );
        loadMethodVisitor.returnValue();

        Label methodEndLabel = loadMethodVisitor.mark();

        loadMethodVisitor.visitLocalVariable(
                "controllerAccessorFactory",
                Type.getType(ControllerAccessorFactory.class).getDescriptor(),
                null,
                methodStartLabel,
                methodEndLabel,
                CONTROLLER_ACCESSOR_FACTORY_LOCAL_INDEX
        );
        loadMethodVisitor.visitLocalVariable(
                "resourceBundle",
                RESOURCE_BUNDLE_D,
                null,
                methodStartLabel,
                methodEndLabel,
                RESOURCE_BUNDLE_LOCAL_INDEX
        );
        loadMethodVisitor.visitLocalVariable(
                "rootInstance",
                rootType.getDescriptor(),
                null,
                methodStartLabel,
                methodEndLabel,
                ROOT_INSTANCE_LOCAL_INDEX
        );

        Type accessorType = Type.getType(ControllerAccessor.class);
        loadMethodVisitor.visitLocalVariable(
                "accessor",
                accessorType.getDescriptor(),
                "L" + accessorType + "<" + controllerType.getDescriptor() + ">;",
                methodStartLabel,
                methodEndLabel,
                ACCESSOR_LOCAL_INDEX
        );
        loadMethodVisitor.visitLocalVariable(
                "controller",
                controllerType.getDescriptor(),
                null,
                methodStartLabel,
                methodEndLabel,
                CONTROLLER_LOCAL_INDEX
        );
        loadMethodVisitor.endMethod();

        renderGetABIVersionMethod();
        renderGetURIMethod();
        renderRequiresExternalControllerMethod();
        renderRequiresResourceBundleMethod();
        renderGetRootInstanceClassMethod();
        renderGetControllerClassMethod();

        loaderWriter.visitEnd();

        try {
            return loaderWriter.toByteArray();
        } finally {
            loadMethodVisitor = null;
        }
    }
}
