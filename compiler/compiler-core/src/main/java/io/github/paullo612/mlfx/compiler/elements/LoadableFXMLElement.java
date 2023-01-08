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
package io.github.paullo612.mlfx.compiler.elements;

import io.github.paullo612.mlfx.api.ControllerAccessor;
import io.github.paullo612.mlfx.compiler.CompilerContext;
import io.github.paullo612.mlfx.expression.Continuation;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.github.paullo612.mlfx.expression.Expressions;
import io.github.paullo612.mlfx.expression.TypedContinuation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ArrayChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableArray;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// Might represent a constructed class, or reference to (read only) instance property. Can have instance or static
//  properties defined.
public abstract class LoadableFXMLElement<P extends FXMLElement<?>> extends FXMLElement<P>
        implements ExpressionContext.Loadable {

    private static final Handle LAMBDA_METAFACTORY_HANDLE = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;",
            false
    );

    static ValueLoader loadElement(CompilerContext context, IdentifiableFXMLElement element) {
        return targetType -> {
            ClassElement elementType = element.getClassElement();

            if (!ElementUtils.isAssignable(elementType, targetType)) {
                ExpressionContext.RenderCommand command = RenderUtils.coerce(elementType, targetType)
                        .orElseThrow(() -> context.compileError(
                                "Attempt to apply property of incompatible type (\""
                                        + ElementUtils.getSimpleName(elementType) + "\")."
                        ));

                return () -> command;
            }

            return element::load;
        };
    }

    static ValueLoader loadString(CompilerContext context, String value) {
        return targetType -> {
            CompilerContext.RenderCommand command =
                    ElementUtils.isAssignable(context.getClassElement(String.class), targetType)
                            ? methodVisitor -> methodVisitor.push(value)
                            : RenderUtils.coerceOrThrow(context, value, targetType);

            return () -> command;
        };
    }

    private enum EventHandlerValueType {
        BINDING_EXPRESSION,
        EXPRESSION,
        CONTROLLER_METHOD_REFERENCE,
        SCRIPT
    }

    interface ValueLoader {

        default List<Continuation> loadReadOnlyList(ClassElement targetType) {
            return Collections.emptyList();
        }

        Continuation load(ClassElement targetType);
    }

    static class ContinuationDelegate implements Continuation {

        private final Continuation continuation;
        private final BiConsumer<GeneratorAdapter, ExpressionContext.RenderCommand> command;

        ContinuationDelegate(
                Continuation continuation,
                BiConsumer<GeneratorAdapter, ExpressionContext.RenderCommand> command) {
            this.continuation = continuation;
            this.command = command;
        }

        @Override
        public boolean canBeRun() {
            return continuation.canBeRun();
        }

        @Override
        public ExpressionContext.RenderCommand run() {
            CompilerContext.RenderCommand renderCommand = continuation.run();

            return methodVisitor -> command.accept(methodVisitor, renderCommand);
        }
    }

    private final List<Continuation> continuations = new ArrayList<>();

    LoadableFXMLElement(P parent) {
        super(parent);
    }

    private MethodElement findAddListenerMethod(
            CompilerContext context,
            ClassElement classElement,
            Class<?>... parameters) {
        return classElement.getEnclosedElement(
                        ElementQuery.ALL_METHODS
                                .onlyInstance()
                                .onlyAccessible()
                                .named(Predicate.isEqual("addListener"))
                                .filter(m -> ElementUtils.isAssignable(m.getReturnType(), void.class))
                                .filter(m -> m.getParameters().length == parameters.length)
                                .filter(m -> {
                                    ParameterElement[] methodParameters = m.getParameters();

                                    for (int i = 0; i < parameters.length; ++i) {
                                        if (!ElementUtils.isAssignable(methodParameters[i].getType(), parameters[i])) {
                                            return false;
                                        }
                                    }

                                    return true;
                                })
                )
                .orElseThrow(() -> context.compileError(
                        "Failed to find addListener(" +
                                Arrays.stream(parameters).
                                        map(Class::getSimpleName)
                                        .collect(Collectors.joining(", "))
                                + ") method in " + ElementUtils.getSimpleName(classElement)
                ));
    }

    private Continuation setControllerMethodReference(
            CompilerContext context,
            ExpressionContext.Loadable loadable,
            Supplier<MethodElement> setterSupplier,
            ExpressionContext.Loadable controller,
            MethodElement controllerMethod,
            Class<?> listenerClass,
            String listenerMethodName,
            Class<?>... listenerParameters) {
        if (!controllerMethod.isReflectionRequired(context.getTargetType())) {
            // Use method reference directly.
            //
            // (...).set(controller::controllerMethod)
            ExpressionContext.RenderCommand load = loadable.load();
            MethodElement setter = setterSupplier.get();

            return () -> methodVisitor -> {
                // Load instance for setter call.
                load.render(methodVisitor);

                // Load controller
                controller.load().render(methodVisitor);

                // Create listener from Controller's method using method reference.
                Type controllerType = RenderUtils.type(controller.getClassElement());

                Type targetMethodType = Type.getType(
                        Arrays.stream(listenerParameters)
                                .map(Type::getType)
                                .map(Type::getDescriptor)
                                .collect(Collectors.joining("", "(", ")V"))
                );

                String implementationMethodDescriptor = Arrays.stream(controllerMethod.getParameters())
                        .map(ParameterElement::getType)
                        .map(RenderUtils::type)
                        .map(Type::getDescriptor)
                        .collect(Collectors.joining("", "(", ")V"));

                methodVisitor.visitInvokeDynamicInsn(
                        // Target method name (name of the method of functional interface).
                        listenerMethodName,
                        // Indy eats Controller and produces listener instance.
                        "(" + controllerType.getDescriptor() + ")" + Type.getType(listenerClass).getDescriptor(),
                        // Bootstrap method: JVM will provide first three arguments, we must provide remaining. We're
                        //  quite happy with default LambdaMetafactory to provide call site for us.
                        LAMBDA_METAFACTORY_HANDLE,
                        // Target method type.
                        targetMethodType,
                        // Handle to implementation. Controller#controllerMethod in our case.
                        new Handle(
                                // Controller should be a class.
                                Opcodes.H_INVOKEVIRTUAL,
                                controllerType.getInternalName(),
                                // We'll be calling controllerMethod method.
                                controllerMethod.getName(),
                                // Compute implementation method descriptor directly from MethodElement, as method
                                //  parameters may be not exactly the same as target method parameters. Target method
                                //  parameter may be assignable to implementation method parameter.
                                implementationMethodDescriptor,
                                false
                        ),
                        Type.getType(implementationMethodDescriptor)
                );

                // And, finally, render setter call.
                RenderUtils.renderMethodCall(methodVisitor, setter);
            };
        }

        if (!ElementUtils.isAccessibleFromFXMLFile(controllerMethod)) {
            // We've found corresponding method, but it is not marked as accessible from FXML document.
            //  Nothing to do here.
            context.warn(
                    "There is \"" + controllerMethod.getName() + "\" method in controller that cannot be called because"
                            + " it is not accessible and not annotated by @FXML annotation.",
                    controllerMethod
            );
            return null;
        }

        // Delegate to accessor.
        //
        // ExecutableMethod method =
        //         accessor.findExecutableMethod(controller, methodName, listenerParameters);
        // (...).set(method::execute)
        ExpressionContext.RenderCommand load = loadable.load();
        MethodElement setter = setterSupplier.get();

        return () -> methodVisitor -> {
            // Load instance for setter call.
            load.render(methodVisitor);

            // Create listener:
            // Load controller accessor
            context.getControllerAccessor().load().render(methodVisitor);
            // Load ControllerAccessor#findMethod's first argument (controller)
            controller.load().render(methodVisitor);
            // Load ControllerAccessor#findMethod's second argument (method name)
            methodVisitor.push(controllerMethod.getName());

            Type classType = Type.getType(Class.class);

            // Load ControllerAccessor#findMethod's third argument (Class<?>[]... )
            methodVisitor.push(controllerMethod.getParameters().length);
            methodVisitor.newArray(classType);

            for (int i = 0; i < controllerMethod.getParameters().length; ++i) {
                methodVisitor.dup();
                methodVisitor.push(i);
                methodVisitor.push(RenderUtils.type(controllerMethod.getParameters()[i].getType()));
                methodVisitor.arrayStore(classType);
            }

            Type executableMethodType = Type.getType(ControllerAccessor.ExecutableMethod.class);

            // Call ControllerAccessor#findMethod. Now we have ControllerAccessor.ExecutableMethod instance at the top
            //  of the stack.
            methodVisitor.invokeInterface(
                    Type.getType(ControllerAccessor.class),
                    new Method(
                            "findMethod",
                            "(" + RenderUtils.OBJECT_D + RenderUtils.STRING_D
                                    + Type.getType(Class[].class).getDescriptor() + ")"
                                    + executableMethodType.getDescriptor()
                    )
            );

            // Create listener from ExecutableMethod's execute method using method reference.
            Type targetMethodType = Type.getType(Arrays.stream(listenerParameters)
                    .map(Type::getType)
                    .map(Type::getDescriptor)
                    .collect(Collectors.joining("", "(", ")V"))
            );

            String implementationDescriptor = "("
                    + RenderUtils.OBJECT_D.repeat(listenerParameters.length) + ")V";

            methodVisitor.visitInvokeDynamicInsn(
                    // Target method name (name of the method of functional interface).
                    listenerMethodName,
                    // Indy eats ExecutableMethod and produces listener instance.
                    "(" + executableMethodType.getDescriptor() + ")"
                            + Type.getType(listenerClass).getDescriptor(),
                    // Bootstrap method: JVM will provide first three arguments, we must provide remaining. We're quite
                    //  happy with default LambdaMetafactory to provide call site for us.
                    LAMBDA_METAFACTORY_HANDLE,
                    // Target method type.
                    targetMethodType,
                    // Handle to implementation. ExecutableMethod#execute in our case.
                    new Handle(
                            // ExecutableMethod is an interface, we don't know an implementation at compile time.
                            Opcodes.H_INVOKEINTERFACE,
                            executableMethodType.getInternalName(),
                            // We'll be calling execute method.
                            "execute",
                            // Which takes as many Object arguments as parameters count.
                            implementationDescriptor,
                            true
                    ),
                    // Same as target method type for simple use-cases.
                    targetMethodType
            );

            // And, finally, render setter call.
            RenderUtils.renderMethodCall(methodVisitor, setter);
        };
    }

    private void handleHandlerAttribute(
            CompilerContext context,
            ExpressionContext.Loadable loadable,
            EventHandlerValueType type,
            String rawValue,
            Class<?> changeListenerClass,
            String changeListenerMethodName,
            Class<?>... changeListenerParameters) {
        switch (type) {
            case EXPRESSION: {
                TypedContinuation expression = Expressions.expression(context, rawValue);

                Continuation continuation = new Continuation() {

                    @Override
                    public boolean canBeRun() {
                        return expression.canBeRun();
                    }

                    private ExpressionContext.RenderCommand addListener(Class<?> listenerClass) {
                        ExpressionContext.RenderCommand load = loadable.load();
                        ExpressionContext.RenderCommand renderCommand = expression.run();

                        MethodElement method =
                                findAddListenerMethod(context, loadable.getClassElement(), listenerClass);

                        return methodVisitor -> {
                            load.render(methodVisitor);
                            renderCommand.render(methodVisitor);
                            RenderUtils.renderMethodCall(methodVisitor, method);
                        };
                    }

                    @Override
                    public ExpressionContext.RenderCommand run() {
                        ClassElement expressionType = expression.getType();
                        if (ElementUtils.isAssignable(expressionType, InvalidationListener.class)) {
                            return addListener(InvalidationListener.class);
                        }

                        if (ElementUtils.isAssignable(expressionType, changeListenerClass)) {
                            return addListener(changeListenerClass);
                        }

                        throw context.compileError(
                                "Incorrect expression result type; Must be one of InvalidationListener or "
                                        + changeListenerClass.getSimpleName() + "."
                        );
                    }
                };

                renderHandler(continuation);
                break;
            }
            case CONTROLLER_METHOD_REFERENCE: {
                ExpressionContext.Loadable controller = context.getScope().get(FXMLLoader.CONTROLLER_KEYWORD);

                if (controller == null) {
                    throw context.compileError(
                            "Controller method \"" + rawValue + "\" referenced, but no controller specified."
                    );
                }

                ClassElement controllerClassElement = controller.getClassElement();
                List<MethodElement> methods = controllerClassElement.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                                .onlyInstance()
                                .named(Predicate.isEqual(rawValue))
                                .filter(m -> ElementUtils.isAssignable(m.getReturnType(), void.class))
                );

                int foundCount = 0;

                outer: for (MethodElement method : methods) {
                    ParameterElement[] parameters = method.getParameters();

                    Continuation continuation = null;

                    if (parameters.length == 1
                            && ElementUtils.isAssignable(parameters[0].getType(), Observable.class)) {
                        setControllerMethodReference(
                                context,
                                loadable,
                                () -> findAddListenerMethod(context, loadable.getClassElement(), changeListenerClass),
                                controller,
                                method,
                                InvalidationListener.class, "invalidated", Observable.class
                        );
                    } else if (parameters.length == changeListenerParameters.length) {
                        for (int i = 0; i < changeListenerParameters.length; ++i) {
                            if (!ElementUtils.isAssignable(parameters[i].getType(), changeListenerParameters[i])) {
                                continue outer;
                            }
                        }

                        continuation = setControllerMethodReference(
                                context,
                                loadable,
                                () -> findAddListenerMethod(context, loadable.getClassElement(), changeListenerClass),
                                controller,
                                method,
                                changeListenerClass, changeListenerMethodName, changeListenerParameters
                        );
                    }

                    if (continuation != null) {
                        renderHandler(continuation);
                        ++foundCount;
                    }

                    if (foundCount == 2) {
                        break;
                    }
                }

                if (foundCount == 0) {
                    throw context.compileError(
                            "Failed to find controller method \"" + rawValue
                                    + "\" compatible with InvalidationListener or "
                                    + changeListenerClass.getSimpleName() + " signature."
                    );
                }

                break;
            }
            default:
                throw new AssertionError("Not reached");
        }
    }

    private boolean handleCollectionHandlerAttribute(
            CompilerContext context,
            String localName,
            EventHandlerValueType type,
            String rawValue) {
        if (!(FXMLLoader.EVENT_HANDLER_PREFIX + FXMLLoader.CHANGE_EVENT_HANDLER_SUFFIX).equals(localName)) {
            return false;
        }

        ClassElement classElement = getClassElement();

        if (ElementUtils.isAssignable(classElement, ObservableList.class)) {
            handleHandlerAttribute(
                    context,
                    this,
                    type,
                    rawValue,
                    ListChangeListener.class,
                    "onChanged",
                    ListChangeListener.Change.class
            );
            return true;
        } else if (ElementUtils.isAssignable(classElement, ObservableMap.class)) {
            handleHandlerAttribute(
                    context,
                    this,
                    type,
                    rawValue,
                    MapChangeListener.class,
                    "onChanged",
                    MapChangeListener.Change.class
            );
            return true;
        } else if (ElementUtils.isAssignable(classElement, ObservableSet.class)) {
            handleHandlerAttribute(
                    context,
                    this,
                    type,
                    rawValue,
                    SetChangeListener.class,
                    "onChanged",
                    SetChangeListener.Change.class
            );
            return true;
        } else if (ElementUtils.isAssignable(classElement, ObservableArray.class)) {
            handleHandlerAttribute(
                    context,
                    this,
                    type,
                    rawValue,
                    ArrayChangeListener.class,
                    "onChanged",
                    ObservableArray.class,
                    boolean.class,
                    int.class,
                    int.class
            );
            return true;
        }

        return false;
    }

    private boolean handlePropertyHandlerAttribute(
            CompilerContext context,
            String localName,
            EventHandlerValueType type,
            String rawValue) {
        // Here we should try to get property model first (from localName), load it and attach ChangeListener.
        if (!localName.endsWith(FXMLLoader.CHANGE_EVENT_HANDLER_SUFFIX)) {
            return false;
        }

        if (localName.length() == FXMLLoader.EVENT_HANDLER_PREFIX.length()
                + FXMLLoader.CHANGE_EVENT_HANDLER_SUFFIX.length()) {
            // "onChange".equals(localName); This is not a property handler attribute.
            return false;
        }

        // Obtain property name.
        String propertyName = localName.substring(FXMLLoader.EVENT_HANDLER_PREFIX.length());
        propertyName =
                propertyName.substring(0, propertyName.length() - FXMLLoader.CHANGE_EVENT_HANDLER_SUFFIX.length());
        propertyName = NameUtils.decapitalize(propertyName);

        String finalPropertyName = propertyName;

        ClassElement classElement = getClassElement();

        // Find property model getter.
        MethodElement propertyModelGetter = classElement.getEnclosedElement(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyAccessible()
                        .named(Predicate.isEqual(finalPropertyName + "Property"))
                        .filter(m -> ElementUtils.isAssignable(m.getReturnType(), ObservableValue.class))
                        .filter(m -> m.getParameters().length == 0)
        )
                .orElseThrow(
                        () -> context.compileError(
                                "Unable to find property model for property \"" + finalPropertyName + "\" of class \""
                                        + ElementUtils.getSimpleName(classElement) + "\"."
                        )
                );

        ExpressionContext.RenderCommand renderCommand = load();

        ExpressionContext.Loadable propertyLoadable = new ExpressionContext.Loadable() {

            @Override
            public ClassElement getClassElement() {
                return propertyModelGetter.getReturnType();
            }

            @Override
            public ExpressionContext.RenderCommand load() {
                return methodVisitor -> {
                    renderCommand.render(methodVisitor);
                    RenderUtils.renderMethodCall(methodVisitor, propertyModelGetter);
                };
            }
        };

        // Add change listener for property.
        handleHandlerAttribute(
                context,
                propertyLoadable,
                type,
                rawValue,
                ChangeListener.class,
                "changed",
                ObservableValue.class,
                Object.class,
                Object.class
        );

        return true;
    }

    private void handleEventPropertyHandlerAttribute(
            CompilerContext context,
            String localName,
            EventHandlerValueType type,
            String rawValue) {
        // Use controller setter to set property.
        ClassElement classElement = getClassElement();

        PropertyElement property = classElement.getBeanProperties().stream()
                .filter(p -> localName.equals(p.getName()))
                .filter(p -> ElementUtils.isAssignable(p.getType(), EventHandler.class))
                .filter(p -> p.getType().getTypeArguments().size() == 0 || p.getType().getTypeArguments().size() == 1)
                .findFirst()
                .map(ElementUtils::fixProperty)
                .orElseThrow(
                        () -> context.compileError(
                                "Unable to find property \"" + localName + "\" of EventHandler type of class \""
                                        + ElementUtils.getSimpleName(classElement) + "\"."
                        )
                );

        MethodElement writeMethod = property.getWriteMethod()
                .orElseThrow(() ->
                        context.compileError(
                                "Property \"" + localName + "\" of class \"" + ElementUtils.getSimpleName(classElement)
                                        + "\" is read-only."
                        )
                );

        switch (type) {
            case EXPRESSION:
                ClassElement eventHandlerClassElement = context.getClassElement(EventHandler.class);
                TypedContinuation expression = Expressions.expression(context, rawValue, eventHandlerClassElement);

                renderHandler(
                        new ContinuationDelegate(
                                expression,
                                (methodVisitor, renderCommand) ->
                                        renderPropertySetter(methodVisitor, renderCommand, writeMethod)
                        )
                );

                break;
            case CONTROLLER_METHOD_REFERENCE:
                ExpressionContext.Loadable controller = context.getScope().get(FXMLLoader.CONTROLLER_KEYWORD);

                if (controller == null) {
                    throw context.compileError(
                            "Controller method \"" + rawValue + "\" referenced, but no controller specified."
                    );
                }

                ClassElement controllerClassElement = controller.getClassElement();
                MethodElement controllerMethod = controllerClassElement.getEnclosedElement(
                                ElementQuery.ALL_METHODS
                                        .onlyInstance()
                                        .named(Predicate.isEqual(rawValue))
                                        .filter(m -> ElementUtils.isAssignable(m.getReturnType(), void.class))
                                        .filter(m -> m.getParameters().length == 1)
                                        .filter(m -> property.getType().getTypeArguments().size() == 0
                                                || ElementUtils.isAssignable(
                                                        m.getParameters()[0].getType(),
                                                        property.getType().getFirstTypeArgument()
                                                                .orElseThrow(AssertionError::new)
                                                )
                                        )
                                        .filter(m ->
                                                ElementUtils.isAssignable(m.getParameters()[0].getType(), Event.class)
                                        )

                        )
                        .orElseThrow(() -> context.compileError(
                                "Failed to find controller method \"" + rawValue
                                        + "\" compatible with EventHandler signature."
                        ));

                Continuation continuation = setControllerMethodReference(
                        context,
                        this,
                        () -> writeMethod,
                        controller,
                        controllerMethod,
                        EventHandler.class,
                        "handle",
                        Event.class
                );

                if (continuation == null) {
                    throw context.compileError(
                            "Failed to find controller method \"" + rawValue
                                    + "\" compatible with EventHandler signature."
                    );
                }

                renderHandler(continuation);
                break;
            default:
                throw new AssertionError("Not reached");
        }
    }

    private EventHandlerValueType computeEventHandlerValueType(String value) {
        if (value.startsWith(FXMLLoader.BINDING_EXPRESSION_PREFIX)) {
            return EventHandlerValueType.BINDING_EXPRESSION;
        }

        // NB: Looks like escape sequences are not applicable here. Looks strange. Escaping $ may be required to not
        //  treat script as expression.
        if (value.startsWith(FXMLLoader.EXPRESSION_PREFIX)) {
            return EventHandlerValueType.EXPRESSION;
        }

        if (value.startsWith(FXMLLoader.CONTROLLER_METHOD_PREFIX)) {
            return EventHandlerValueType.CONTROLLER_METHOD_REFERENCE;
        }

        return EventHandlerValueType.SCRIPT;
    }

    private void handleEventHandlerAttribute(CompilerContext context, String localName, String value) {
        EventHandlerValueType type = computeEventHandlerValueType(value);

        String rawValue;

        switch (type) {
            case BINDING_EXPRESSION:
                throw context.compileError("Binding expressions are not supported as event handlers.");
            case SCRIPT:
                throw context.compileError("Scripts are not supported as event handlers yet.");
            case EXPRESSION:
                rawValue = value.substring(FXMLLoader.EXPRESSION_PREFIX.length());
                break;
            case CONTROLLER_METHOD_REFERENCE:
                rawValue = value.substring(FXMLLoader.CONTROLLER_METHOD_PREFIX.length());
                break;
            default:
                throw new AssertionError("Not reached.");
        }

        // There could be collections event handlers (onChange + observable collection), property handlers
        //  (onPropertyNameChanged) and simple event handlers (onPropertyOfEventTypeName). We should take in account
        //  that there might be property of Event type named "onChange" or "onSomethingChanged", and event handler must
        //  still apply to it.
        if (handleCollectionHandlerAttribute(context, localName, type, rawValue)) {
            return;
        }

        if (handlePropertyHandlerAttribute(context, localName, type, rawValue)) {
            return;
        }

        handleEventPropertyHandlerAttribute(context, localName, type, rawValue);
    }

    void render(CompilerContext context, Continuation continuation) {
        if (continuation.canBeRun()) {
            context.getRenderer().render(continuation.run());
            return;
        }

        continuations.add(continuation);
    }

    void renderHandler(Continuation continuation) {
        // NB: Postpone handler rendering till element end to avoid side effects while loading.
        continuations.add(continuation);
    }

    void renderPropertySetter(
            GeneratorAdapter methodVisitor,
            ExpressionContext.RenderCommand command,
            MethodElement writeMethod) {
        load().render(methodVisitor);
        command.render(methodVisitor);

        RenderUtils.renderMethodCall(methodVisitor, writeMethod);
    }

    boolean isBindingExpression(String value) {
        return value.startsWith(FXMLLoader.BINDING_EXPRESSION_PREFIX)
                && value.endsWith(FXMLLoader.BINDING_EXPRESSION_SUFFIX);
    }

    private void handleBindingExpression(CompilerContext context, PropertyElement property, String value) {
        // NB: This is kinda quirky. We should apply instance property if it is a static biding expression, but should
        //  bind to a property if expression is dynamic. Binding type can only be determined when it can be run, so, we
        //  cannot apply all the logic right there.
        Optional<MethodElement> writeMethod = property.getWriteMethod();

        if (writeMethod.isEmpty()) {
            throw context.compileError(
                    "Cannot bind to read-only property \"" + property.getName() + "\" of class \"" + getClassElement()
                            + "\"."
            );
        }

        String expressionValue = value.substring(
                FXMLLoader.BINDING_EXPRESSION_PREFIX.length(),
                value.length() - FXMLLoader.BINDING_EXPRESSION_SUFFIX.length()
        );

        if (expressionValue.length() == 0) {
            throw context.compileError("Missing expression.");
        }

        Expressions.Binding binding = Expressions.binding(context, expressionValue, property.getType());

        render(context, new Continuation() {

            @Override
            public boolean canBeRun() {
                return binding.canBeRun();
            }

            @Override
            public ExpressionContext.RenderCommand run() {
                if (binding.getBindingType() == Expressions.BindingType.STATIC) {
                    // It's a static binding. Render property setter.
                    return methodVisitor ->
                            renderPropertySetter(methodVisitor, binding.run(), writeMethod.get());
                }

                // It's a dynamic binding. Bind to property using Property's bind method.
                ClassElement unboxedType = property.getType();

                if (ElementUtils.isPrimitive(property.getType())) {
                    for (Map.Entry<Class<?>, PrimitiveElement> entry : RenderUtils.BOXED_PRIMITIVES.entrySet()) {
                        if (entry.getValue().isAssignable(unboxedType)) {
                            unboxedType = context.getClassElement(entry.getKey());
                            break;
                        }
                    }
                }

                ClassElement propertyClass = context.getClassElement(Property.class);
                ClassElement genericPropertyClass = propertyClass.withBoundGenericTypes(List.of(unboxedType));

                ClassElement instance = getClassElement();
                MethodElement methodElement = instance.getEnclosedElement(
                                ElementQuery.ALL_METHODS
                                        .onlyAccessible()
                                        .onlyInstance()
                                        .named(Predicate.isEqual(property.getName() + "Property"))
                                        .filter(m -> m.getParameters().length == 0)
                                        .filter(m -> ElementUtils.isAssignable(m.getReturnType(), genericPropertyClass))
                        )
                        .orElseThrow(() -> context.compileError(
                                "Unable to find model for property \"" + property.getName() + "\" in class \""
                                        + instance.getName() + "\"."
                        ));

                ExpressionContext.RenderCommand command = binding.run();

                return methodVisitor -> {
                    load().render(methodVisitor);
                    RenderUtils.renderMethodCall(methodVisitor, methodElement);
                    command.render(methodVisitor);
                    methodVisitor.invokeInterface(
                            Type.getType(Property.class),
                            new Method("bind", "(" + Type.getType(ObservableValue.class).getDescriptor() + ")V")
                    );
                };
            }
        });
    }

    Optional<PropertyElement> findDefaultProperty(CompilerContext context, ClassElement classElement) {
        Optional<AnnotationValue<DefaultProperty>> defaultProperty = classElement.findAnnotation(DefaultProperty.class);

        if (defaultProperty.isEmpty()) {
            return Optional.empty();
        }

        String defaultPropertyName = defaultProperty.get().getRequiredValue(String.class);

        return Optional.of(
                findProperty(defaultPropertyName)
                        .orElseThrow(() -> context.compileError(
                                "No default property \"" + defaultPropertyName + "\" found in class \"" +
                                        ElementUtils.getSimpleName(classElement) + "\"."
                        ))
        );
    }

    void handleReadOnlyInstancePropertyAttribute(
            CompilerContext context,
            ClassElement type,
            ValueLoader loader) {
        if (ElementUtils.isAssignable(type, List.class)) {
            ClassElement listType = type.getFirstTypeArgument()
                    .filter(e -> !e.isGenericPlaceholder())
                    .orElseGet(() -> context.getClassElement(Object.class));

            // Try to add element sequence to List first, add single element if element is not a sequence.
            List<Continuation> loadContinuations = loader.loadReadOnlyList(listType);
            if (loadContinuations.isEmpty()) {
                loadContinuations = Collections.singletonList(loader.load(listType));
            }

            MethodElement addMethod = type.getEnclosedElement(
                            ElementQuery.ALL_METHODS
                                    .onlyInstance()
                                    .onlyAccessible()
                                    .named(Predicate.isEqual("add"))
                                    .filter(m -> m.getParameters().length == 1)
                                    .filter(m ->
                                            ElementUtils.isAssignable(m.getParameters()[0].getType(), Object.class)
                                    )
                    )
                    .orElseThrow(() -> context.compileError("No add method found in List interface."));

            for (Continuation loadContinuation : loadContinuations) {
                render(context, new ContinuationDelegate(loadContinuation, (methodVisitor, command) -> {
                    load().render(methodVisitor);
                    command.render(methodVisitor);

                    RenderUtils.renderMethodCall(methodVisitor, addMethod);
                }));
            }

            return;
        }

        // Search for default property and try to apply value recursively
        PropertyElement nestedProperty = findDefaultProperty(context, type)
                .orElseThrow(() -> context.compileError(
                        "Failed to set property value of class \"" + ElementUtils.getSimpleName(getClassElement())
                                + "\": \"" + ElementUtils.getSimpleName(type)
                                + "\" not a List and has no @DefaultProperty annotation."
                ));

        applyInstanceProperty(context, type, nestedProperty, loader);
    }

    void applyInstanceProperty(
            CompilerContext context,
            ClassElement instance,
            PropertyElement property,
            ValueLoader loader) {
        Optional<MethodElement> readMethod = property.getReadMethod();
        Optional<MethodElement> writeMethod = property.getWriteMethod();

        if (readMethod.isPresent() && writeMethod.isEmpty()) {
            MethodElement read = readMethod.get();

            handleReadOnlyInstancePropertyAttribute(context, read.getReturnType(), new ValueLoader() {

                private Continuation delegate(Continuation continuation) {
                    return new ContinuationDelegate(continuation, (methodVisitor, command) -> {
                        RenderUtils.renderMethodCall(methodVisitor, read);
                        command.render(methodVisitor);
                    });
                }

                @Override
                public List<Continuation> loadReadOnlyList(ClassElement targetType) {
                    List<Continuation> result = loader.loadReadOnlyList(targetType);
                    if (result.isEmpty()) {
                        return result;
                    }
                    return result.stream()
                            .map(this::delegate)
                            .collect(Collectors.toList());
                }

                @Override
                public Continuation load(ClassElement targetType) {
                    return delegate(loader.load(targetType));
                }
            });

            return;
        }

        if (writeMethod.isPresent()) {
            handleInstancePropertyAttribute(context, property, writeMethod.get(), loader);
            return;
        }

        throw context.compileError(
                "Malformed property \"" + property.getName() + "\" of class \""
                        + ElementUtils.getSimpleName(instance) + "\"."
        );
    }

    private void handleInstancePropertyAttribute(CompilerContext context, String localName, String value) {
        PropertyElement property = findProperty(localName)
                .orElseThrow(() -> context.compileError("Unable to find property \"" + localName + "\"."));

        if (isBindingExpression(value)) {
            handleBindingExpression(context, property, value);
            return;
        }

        apply(context, property, new PropertyValueLoader(context, value));
    }

    private void handleInstancePropertyAttribute(
            CompilerContext context,
            PropertyElement property,
            MethodElement writeMethod,
            ValueLoader loader) {
        Continuation loadContinuation = loader.load(property.getType());

        render(context, new ContinuationDelegate(loadContinuation, (methodVisitor, command) ->
                renderPropertySetter(methodVisitor, command, writeMethod)
        ));
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        if (prefix != null && !prefix.isEmpty()) {
            throw context.compileError(prefix + ":" + localName + " is not a valid attribute.");
        }

        if (localName.startsWith(FXMLLoader.EVENT_HANDLER_PREFIX)) {
            handleEventHandlerAttribute(context, localName, value);
            return;
        }

        int i = localName.lastIndexOf('.');

        if (i == -1) {
            handleInstancePropertyAttribute(context, localName, value);
            return;
        }

        String name = localName.substring(i + 1);

        ClassElement classElement = context.getFXMLClassElement(localName.substring(0, i))
                .orElseThrow(() -> context.compileError(localName + " is not a valid attribute."));

        // NB: Static property attributes cannot have binding expressions in them, as static property resolves to static
        //  method call. There is simply nothing to bind to.
        if (isBindingExpression(value)) {
            throw context.compileError("Cannot bind to static property.");
        }

        // NB: FXMLLoader does not support coercion of |,| separated strings to String array. We do.
        apply(context, classElement, name, new PropertyValueLoader(context, value));
    }

    @Override
    public void handleEndElement(CompilerContext context) {
        // Apply continuations unconditionally. If continuation cannot be rendered now, it is a compilation error.
        for (Continuation continuation : continuations) {
            context.getRenderer().render(continuation.run());
        }
    }

    @Override
    public LoadableFXMLElement<?> asLoadableFXMLElement() {
        return this;
    }

    public Optional<PropertyElement> findProperty(String name) {
        for (PropertyElement property : getClassElement().getBeanProperties()) {
            if (property.getName().equals(name)) {
                return Optional.of(ElementUtils.fixProperty(property));
            }
        }

        return Optional.empty();
    }

    void apply(CompilerContext context, PropertyElement property, ValueLoader loader) {
        applyInstanceProperty(context, getClassElement(), property, loader);
    }

    void apply(CompilerContext context, ReadWriteInstanceProperty property, ValueLoader loader) {
        applyInstanceProperty(context, getClassElement(), property.getPropertyElement(), loader);
    }

    void apply(CompilerContext context, ReadOnlyInstanceProperty property, ValueLoader loader) {
        property.handleReadOnlyInstancePropertyAttribute(context, property.getClassElement(), loader);
    }

    void apply(CompilerContext context, ClassElement classElement, String name, ValueLoader valueLoader) {
        ClassElement currentClassElement = getClassElement();

        // NB: Infer static property type from getter return type.
        String getterName = NameUtils.getterNameFor(name);
        String booleanGetterName = NameUtils.getterNameFor(name, true);

        List<MethodElement> staticGetters = classElement.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .modifiers(m -> m.contains(ElementModifier.STATIC))
                        .onlyAccessible()
                        .named(n -> n.equals(getterName) || n.equals(booleanGetterName))
                        .filter(m -> m.getParameters().length == 1)
                        .filter(m -> ElementUtils.isAssignable(currentClassElement, m.getParameters()[0].getType()))
        );

        if (staticGetters.isEmpty()) {
            // FXMLLoader throws |com.sun.javafx.fxml.PropertyNotFoundException| in such case. We're inside annotation
            //  processor, so, we can throw |CompileErrorException| instead.
            throw context.compileError("Static property \"" + name + "\" is read-only.");
        }

        ClassElement staticPropertyType = staticGetters.get(0).getReturnType();

        // |get| prefixed method takes precedence over |is| prefixed if there are multiple methods found.
        if (staticGetters.size() > 1) {
            for (MethodElement staticGetter : staticGetters) {
                if (staticGetter.getName().equals(getterName)) {
                    staticPropertyType = staticGetter.getReturnType();
                    break;
                }
            }
        }

        String setterName = NameUtils.setterNameFor(name);
        ClassElement finalStaticPropertyType = staticPropertyType;

        MethodElement staticSetter = classElement.getEnclosedElement(
                        ElementQuery.ALL_METHODS
                                .modifiers(m -> m.contains(ElementModifier.STATIC))
                                .onlyAccessible()
                                .named(Predicate.isEqual(setterName))
                                .filter(m -> m.getParameters().length == 2)
                                .filter(m ->
                                        ElementUtils.isAssignable(currentClassElement, m.getParameters()[0].getType())
                                )
                                .filter(m ->
                                        ElementUtils.isAssignable(
                                                finalStaticPropertyType,
                                                m.getParameters()[1].getType()
                                        )
                                )
                )
                .orElseThrow(() -> context.compileError("Static property \"" + name + "\" does not exist"));

        Continuation continuation = valueLoader.load(staticPropertyType);

        render(context, new ContinuationDelegate(continuation, (methodVisitor, command) -> {
            load().render(methodVisitor);
            command.render(methodVisitor);

            methodVisitor.invokeStatic(
                    RenderUtils.type(staticSetter.getDeclaringType()),
                    new Method(
                            setterName,
                            "(" + RenderUtils.type(staticSetter.getParameters()[0].getType()).getDescriptor()
                                    + RenderUtils.type(staticSetter.getParameters()[1].getType()).getDescriptor() + ")V"
                    )
            );
        }));
    }
}
