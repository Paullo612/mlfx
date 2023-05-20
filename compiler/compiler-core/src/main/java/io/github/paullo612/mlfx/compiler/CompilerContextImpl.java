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

import io.github.paullo612.mlfx.api.ControllerAccessor;
import io.github.paullo612.mlfx.api.ControllerAccessorFactory;
import io.github.paullo612.mlfx.api.ObservableListenerHelper;
import io.github.paullo612.mlfx.compiler.elements.ElementUtils;
import io.github.paullo612.mlfx.compiler.elements.FXMLElement;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.visitor.VisitorContext;
import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Predicate;

class CompilerContextImpl implements CompilerContext {

    interface Warner {
        void warn(String message);
    }

    private final VisitorContext visitorContext;
    private final TaskFactory taskFactory;
    private final Warner warner;
    private final ClassElement targetType;
    private final Charset charset;

    private final List<String> importedPackages = new ArrayList<>();
    private final Map<String, ClassElement> importedClasses = new HashMap<>();

    private final Map<String, Loadable> scope = new HashMap<>();
    private final List<BindingExpressionRendererImpl> expressionRenderers = new ArrayList<>();

    private final Loadable controllerAccessor = new Loadable() {

        @Override
        public ClassElement getClassElement() {
            return CompilerContextImpl.this.getClassElement(ControllerAccessor.class);
        }

        @Override
        public RenderCommand load() {
            return RootRenderer::loadAccessor;
        }
    };

    private final Loadable controllerAccessorFactory = new Loadable() {

        @Override
        public ClassElement getClassElement() {
            return CompilerContextImpl.this.getClassElement(ControllerAccessorFactory.class);
        }

        @Override
        public RenderCommand load() {
            return RootRenderer::loadControllerAccessorFactory;
        }
    };

    private final Loadable nonRequiredResourceBundle = new Loadable() {
        @Override
        public ClassElement getClassElement() {
            return CompilerContextImpl.this.getClassElement(ResourceBundle.class);
        }

        @Override
        public RenderCommand load() {
            return RootRenderer::loadNonRequiredResourceBundle;
        }
    };

    private FXMLElement<?> currentFXMLElement;

    private boolean hasFxRoot;
    private boolean requiresExternalController;
    private Loadable rootLoadable;

    private Renderer renderer;
    private final BitSet slots = new BitSet();
    private int expressionCounter;

    CompilerContextImpl(
            VisitorContext visitorContext,
            TaskFactory taskFactory,
            Warner warner,
            ClassElement targetType,
            Charset charset,
            RootRenderer rootRenderer) {
        this.visitorContext = visitorContext;
        this.taskFactory = taskFactory;
        this.warner = warner;
        this.targetType = targetType;
        this.charset = charset;
        this.renderer = rootRenderer;

        // Location
        scope.put(FXMLLoader.LOCATION_KEY, new Loadable() {

            @Override
            public ClassElement getClassElement() {
                return CompilerContextImpl.this.getClassElement(URL.class);
            }

            @Override
            public RenderCommand load() {
                return rootRenderer::loadLocation;
            }
        });

        // Resource bundle
        scope.put(FXMLLoader.RESOURCES_KEY, new Loadable() {

            @Override
            public ClassElement getClassElement() {
                return CompilerContextImpl.this.getClassElement(ResourceBundle.class);
            }

            @Override
            public RenderCommand load() {
                return rootRenderer::loadResourceBundle;
            }
        });
    }

    FXMLElement<?> getCurrentFXMLElement() {
        return currentFXMLElement;
    }

    void setCurrentFXMLElement(FXMLElement<?> currentFXMLElement) {
        this.currentFXMLElement = currentFXMLElement;
    }

    @Override
    public CompileErrorException compileError(String message) {
        return new CompileErrorException(message);
    }

    @Override
    public void warn(String message) {
        warner.warn(message);
    }

    @Override
    public void warn(String message, Element element) {
        visitorContext.warn(message, element);
    }

    private void importPackage(String name) {
        importedPackages.add(name);
    }

    private Optional<ClassElement> loadClassElement(String packageName, String className) {
        return visitorContext.getClassElement(packageName + "." + className.replace('.', '$'));
    }

    private Optional<ClassElement> loadFullyQualifiedClassElement(String name, boolean doImport) {
        // NB: We still want to be able to load inner classes. From FXML point of view, inner class name would be
        // foo.Bar.Baz, so, we cannot blindly use lastIndexOf('.') here, as this will result in foo.Bar as package
        //  name.
        int dot = name.lastIndexOf('.');

        if (dot == name.length() - 1) {
            // Name ends with dot. Not a valid class name.
            return Optional.empty();
        }

        while (dot > 0) {
            int newDot = name.lastIndexOf('.', dot - 1);

            if (!Character.isUpperCase(name.charAt(newDot + 1))) {
                break;
            }

            dot = newDot;
        }

        if (dot == -1 || dot == 0) {
            // Name starts with dot, it is a package name without class name, or there is no dots in name at all.
            return Optional.empty();
        }

        String packageName = name.substring(0, dot);
        String className = name.substring(dot + 1);

        Optional<ClassElement> classElement = loadClassElement(packageName, className);

        if (doImport) {
            classElement.ifPresent(element -> importedClasses.put(className, element));
        }

        return classElement;
    }

    @Override
    public ClassElement getTargetType() {
        return targetType;
    }

    @Override
    public Optional<Loadable> declareFxRootElement() {
        hasFxRoot = true;

        return Optional.ofNullable(rootLoadable);
    }

    @Override
    public Loadable declareFxController(ClassElement classElement) {
        if (classElement.isInterface()) {
            throw compileError("Controller \"" + classElement.getName() + "\" must be a class.");
        }

        return new Loadable() {
            @Override
            public ClassElement getClassElement() {
                return classElement;
            }

            @Override
            public RenderCommand load() {
                return RootRenderer::loadController;
            }
        };
    }

    Loadable getRootLoadable() {
        return rootLoadable;
    }

    boolean hasFxRoot() {
        return hasFxRoot;
    }

    boolean requiresExternalController() {
        return requiresExternalController;
    }

    @Override
    public Optional<ClassElement> getFXMLClassElement(String name) {
        // Check if this is a fully-qualified class name
        if (!Character.isUpperCase(name.charAt(0))) {
            return loadFullyQualifiedClassElement(name, false);
        }

        // This is an unqualified class name. Use class cache.
        ClassElement element = importedClasses.computeIfAbsent(name, n -> {
            for (String packageName : importedPackages) {
                Optional<ClassElement> packageElement = loadClassElement(packageName, name);
                if (packageElement.isPresent()) {
                    return packageElement.get();
                }
            }

            return null;
        });

        return Optional.ofNullable(element);
    }

    @Override
    public ClassElement getClassElement(Class<?> classElement) {
        return visitorContext.getClassElement(classElement)
                .orElseThrow(() -> new AssertionError(
                        "Unable to get class element for class \"" + classElement.getSimpleName() + "\"."
                ));
    }

    private void doSetControllerField(FieldElement field, Loadable value, Loadable controller) {
        if (!field.isReflectionRequired(targetType)) {
            // Render setter directly.
            renderer.render(methodVisitor -> {
                controller.load().render(methodVisitor);
                value.load().render(methodVisitor);

                methodVisitor.putField(
                        RenderUtils.type(field.getOwningType()),
                        field.getName(),
                        RenderUtils.type(field.getType())
                );
            });

            return;
        }

        if (!ElementUtils.isAccessibleFromFXMLFile(field)) {
            // We've found corresponding field, but it is not marked as accessible from FXML document. Nothing to do
            //  here.
            warn(
                    "There is field named \"" + field.getName()
                            + "\" in controller that cannot be set because it is not accessible and not annotated by "
                            + " @FXML annotation.",
                    field
            );
            return;
        }

        // Delegate to accessor.
        renderer.render(methodVisitor -> {
            getControllerAccessor().load().render(methodVisitor);

            controller.load().render(methodVisitor);
            methodVisitor.push(field.getName());
            value.load().render(methodVisitor);

            methodVisitor.invokeInterface(
                    Type.getType(ControllerAccessor.class),
                    new Method(
                            "setField",
                            "(" + RenderUtils.OBJECT_D + RenderUtils.STRING_D + RenderUtils.OBJECT_D + ")V"
                    )
            );
        });
    }

    @Override
    public void setControllerField(String name, Loadable value) {
        Loadable controller = scope.get(FXMLLoader.CONTROLLER_KEYWORD);

        if (controller == null) {
            // No controller present, so, nowhere to set.
            return;
        }

        // NB: No coercion here.
        controller.getClassElement().getEnclosedElement(
                        ElementQuery.ALL_FIELDS
                                .onlyInstance()
                                .named(Predicate.isEqual(name))
                                .filter(f -> ElementUtils.isAssignable(value.getClassElement(), f.getType()))
                )
                .ifPresent(f -> doSetControllerField(f, value, controller));
    }

    @Override
    public Loadable getNonRequiredResourceBundle() {
        return nonRequiredResourceBundle;
    }

    @Override
    public Loadable getControllerAccessor() {
        return controllerAccessor;
    }

    @Override
    public Loadable getControllerAccessorFactory() {
        return controllerAccessorFactory;
    }

    private void importClass(String name) {
        loadFullyQualifiedClassElement(name, true)
                .orElseThrow(() -> compileError("Class " + name + " is not found on classpath."));
    }

    void handleImport(String value) {
        if (value.endsWith(".*")) {
            importPackage(value.substring(0, value.length() - 2));
        } else {
            importClass(value);
        }
    }

    void handleRootType(String value) {
        ClassElement rootClassElement = getFXMLClassElement(value)
                .orElseThrow(() -> compileError("Invalid type \"" + value + "\"."));

        rootLoadable = new Loadable() {
            @Override
            public ClassElement getClassElement() {
                return rootClassElement;
            }

            @Override
            public RenderCommand load() {
                return RootRenderer::loadRootInstance;
            }
        };
    }

    void handleControllerType(String value) {
        ClassElement controllerClassElement = getFXMLClassElement(value)
                .orElseThrow(() -> compileError("Invalid type \"" + value + "\"."));

        requiresExternalController = true;
        scope.put(FXMLLoader.CONTROLLER_KEYWORD, declareFxController(controllerClassElement));
    }

    @Override
    public Map<String, Loadable> getScope() {
        return scope;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public Optional<CompileTask> getCompileTask(URI location) {
        return taskFactory.getTask(location);
    }

    @Override
    public Renderer getRenderer() {
        return renderer;
    }

    @Override
    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public int getDefaultSlot() {
        return RootRenderer.LAST_LOCAL_INDEX + 1;
    }

    @Override
    public int acquireSlot() {
        int slot = slots.nextClearBit(0);
        slots.set(slot);
        return slot + getDefaultSlot();
    }

    @Override
    public void releaseSlot(int slot) {
        slot = slot - getDefaultSlot();
        slots.clear(slot);
    }

    @Override
    public BindingExpressionRenderer createExpressionRenderer(
            String expression,
            Class<? extends Property> parentClass,
            ClassElement genericType) {
        BindingExpressionRendererImpl expressionRenderer = new BindingExpressionRendererImpl(
                targetType,
                "Expression" + (expressionCounter++),
                expression,
                parentClass,
                genericType,
                getClassElement(ObservableListenerHelper.class)
        );

        expressionRenderers.add(expressionRenderer);
        return expressionRenderer;
    }

    List<BindingExpressionRendererImpl> getExpressionRenderers() {
        return expressionRenderers;
    }
}
