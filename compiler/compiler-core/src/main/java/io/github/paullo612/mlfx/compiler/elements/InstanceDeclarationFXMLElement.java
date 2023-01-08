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

import io.github.paullo612.mlfx.compiler.CompilerContext;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import javafx.beans.NamedArg;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InstanceDeclarationFXMLElement extends IdentifiableFXMLElement {

    private static class SeenProperty {
        final PropertyElement propertyElement;
        final ValueLoader loader;
        final ExpressionContext.RenderCommand createCommand;
        final BiConsumer<CompilerContext, ValueLoader> action;

        SeenProperty(
                PropertyElement propertyElement,
                ValueLoader loader,
                ExpressionContext.RenderCommand createCommand,
                BiConsumer<CompilerContext, ValueLoader> action) {
            this.propertyElement = propertyElement;
            this.loader = loader;
            this.createCommand = createCommand;
            this.action = action;
        }
    }

    // NB: We must collect all apply calls till element is fully processed, because we want to set as match values as
    //  possible through constructor.
    private class PropertyInterceptor implements CompilerContext.Renderer {

        private final List<ConstructorElement> constructors;
        final CompilerContext.Renderer parent;

        private final List<ExpressionContext.RenderCommand> commands = new ArrayList<>();
        private List<ExpressionContext.RenderCommand> currentCommands = new ArrayList<>();

        private final Map<String, List<SeenProperty>> seenProperties = new LinkedHashMap<>();

        PropertyInterceptor(CompilerContext context, List<ConstructorElement> constructors) {
            this.constructors = constructors;
            this.parent = context.getRenderer();

            context.setRenderer(this);
        }

        @Override
        public void render(ExpressionContext.RenderCommand command) {
            currentCommands.add(command);
        }

        private void doIntercept(
                CompilerContext context,
                PropertyElement property,
                ValueLoader loader,
                BiConsumer<CompilerContext, ValueLoader> action) {
            if (!constructorArgNames.contains(property.getName())) {
                // This property does not have constructor argument. Not interested in.
                action.accept(context, loader);
                renderAsIs();
                return;
            }

            ExpressionContext.RenderCommand createCommand = null;
            if (!currentCommands.isEmpty()) {
                List<ExpressionContext.RenderCommand> currentCommands = this.currentCommands;

                createCommand = methodVisitor -> currentCommands.forEach(c -> c.render(methodVisitor));
                this.currentCommands = new ArrayList<>();
            }

            SeenProperty seenProperty = new SeenProperty(
                    property,
                    loader, createCommand,
                    action
            );

            List<SeenProperty> seenPropertiesList =
                    seenProperties.computeIfAbsent(property.getName(), __ -> new ArrayList<>());

            seenPropertiesList.add(seenProperty);
        }

        void intercept(CompilerContext context, PropertyElement property, ValueLoader loader) {
            doIntercept(
                    context,
                    property,
                    loader,
                    (c, l) -> InstanceDeclarationFXMLElement.super.apply(c, property, l)
            );
        }

        void intercept(CompilerContext context, ReadOnlyInstanceProperty property, ValueLoader loader) {
            doIntercept(
                    context,
                    property.getPropertyElement(),
                    loader,
                    (c, l) -> InstanceDeclarationFXMLElement.super.apply(c, property, l)
            );
        }

        void intercept(CompilerContext context, ReadWriteInstanceProperty property, ValueLoader loader) {
            doIntercept(
                    context,
                    property.getPropertyElement(),
                    loader,
                    (c, l) -> InstanceDeclarationFXMLElement.super.apply(c, property, l)
            );
        }

        void intercept(CompilerContext context, ClassElement classElement, String name, ValueLoader loader) {
            InstanceDeclarationFXMLElement.super.apply(context, classElement, name, loader);
            renderAsIs();
        }

        void intercept(CompilerContext context, IdentifiableFXMLElement element) {
            InstanceDeclarationFXMLElement.super.apply(context, element);
            renderAsIs();
        }

        private void renderAsIs() {
            // Render as is after object construction.
            if (!currentCommands.isEmpty()) {
                List<ExpressionContext.RenderCommand> currentCommands = this.currentCommands;

                commands.add(methodVisitor -> currentCommands.forEach(c -> c.render(methodVisitor)));
                this.currentCommands = new ArrayList<>();
            }
        }

        private List<ConstructorElement> findConstructorsExactMatch() {
            Set<String> seenNames = seenProperties.keySet();

            List<ConstructorElement> result = new ArrayList<>();

            for (ConstructorElement constructor : constructors) {
                Set<String> names = Arrays.stream(constructor.getParameters())
                        .map(p -> p.findDeclaredAnnotation(NamedArg.class)
                                .flatMap(v -> v.getValue(String.class))
                                .orElseThrow(AssertionError::new)
                        )
                        .collect(Collectors.toSet());

                if (seenNames.equals(names)) {
                    result.add(constructor);
                }
            }

            return result;
        }

        private List<ConstructorElement> findConstructorsForSeenProperties() {
            Set<String> roPropertyNames = seenProperties.entrySet().stream()
                    .filter(e -> e.getValue().stream()
                            .map(p -> p.propertyElement)
                            .anyMatch(PropertyElement::isReadOnly)
                    )
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            class RankedConstructor {
                final ConstructorElement constructorElement;
                final int propertiesCanBeSetCount;
                final int defaultValuesCount;

                RankedConstructor(
                        ConstructorElement constructorElement,
                        int propertiesCanBeSetCount,
                        int defaultValuesCount) {
                    this.constructorElement = constructorElement;
                    this.propertiesCanBeSetCount = propertiesCanBeSetCount;
                    this.defaultValuesCount = defaultValuesCount;
                }
            }

            List<RankedConstructor> rankedConstructors = new ArrayList<>();

            for (ConstructorElement constructor : constructors) {
                if (constructor.getParameters().length == 0) {
                    // Not interested in no-args constructor.
                    continue;
                }

                int roPropertiesCount = 0;

                int propertiesCanBeSetCount = 0;
                int defaultValuesCount = 0;

                for (ParameterElement parameter : constructor.getParameters()) {
                    AnnotationValue<NamedArg> namedArg = parameter.findDeclaredAnnotation(NamedArg.class)
                            .orElseThrow(AssertionError::new);

                    String namedArgValue = namedArg.getValue(String.class)
                            .orElseThrow(AssertionError::new);

                    if (!seenProperties.containsKey(namedArgValue)) {
                       ++defaultValuesCount;
                   } else {
                       ++propertiesCanBeSetCount;
                   }

                   if (roPropertyNames.contains(namedArgValue)) {
                       ++roPropertiesCount;
                   }
                }

                if (roPropertiesCount != roPropertyNames.size()) {
                    // We cannot set all RO properties specified in FXML document using this constructor. Skip.
                    continue;
                }

                rankedConstructors.add(new RankedConstructor(constructor, propertiesCanBeSetCount, defaultValuesCount));
            }

            return rankedConstructors.stream()
                    .sorted(Comparator.<RankedConstructor>comparingInt(r -> r.propertiesCanBeSetCount)
                            .reversed()
                            .thenComparingInt(r -> r.defaultValuesCount)
                    )
                    .map(r -> r.constructorElement)
                    .collect(Collectors.toList());
        }

        private boolean construct(CompilerContext context, ConstructorElement constructorElement) {
            Set<String> constructorArguments = new HashSet<>();

            List<ExpressionContext.RenderCommand> toRender = new ArrayList<>(constructorElement.getParameters().length);

            for (ParameterElement parameter : constructorElement.getParameters()) {
                AnnotationValue<NamedArg> namedArg = parameter.findDeclaredAnnotation(NamedArg.class)
                        .orElseThrow(AssertionError::new);

                String namedArgValue = namedArg.getValue(String.class)
                        .orElseThrow(AssertionError::new);

                constructorArguments.add(namedArgValue);
                ClassElement parameterClassElement = parameter.getType();

                ExpressionContext.RenderCommand renderCommand;

                List<SeenProperty> properties = seenProperties.get(namedArgValue);
                if (properties != null) {
                    // We've seen this property. Render it.

                    if (ElementUtils.isAssignable(parameterClassElement, List.class)) {
                        ClassElement listType = parameterClassElement.getFirstTypeArgument()
                                .filter(e -> !e.isGenericPlaceholder())
                                .orElseGet(() -> context.getClassElement(Object.class));

                        ClassElement arrayListClassElement = context.getClassElement(ArrayList.class);

                        MethodElement addMethod = arrayListClassElement.getEnclosedElement(
                                        ElementQuery.ALL_METHODS
                                                .onlyInstance()
                                                .onlyAccessible()
                                                .named(Predicate.isEqual("add"))
                                                .filter(m -> m.getParameters().length == 1)
                                                .filter(m -> ElementUtils.isAssignable(
                                                        m.getParameters()[0].getType(), Object.class
                                                ))
                                )
                                .orElseThrow(() ->
                                        context.compileError(
                                                "No add method found in "
                                                        + ElementUtils.getSimpleName(arrayListClassElement) + " class."
                                        )
                                );

                        List<ExpressionContext.RenderCommand> propertiesCommands = properties.stream()
                                .<ExpressionContext.RenderCommand>map(property -> {
                                    ExpressionContext.RenderCommand loadCommand = property.loader.load(listType).run();

                                    return methodVisitor -> {
                                        if (property.createCommand != null) {
                                            property.createCommand.render(methodVisitor);
                                        }

                                        loadCommand.render(methodVisitor);
                                    };
                                })
                                .collect(Collectors.toList());

                        renderCommand = methodVisitor -> {
                            Type arrayListType = RenderUtils.type(arrayListClassElement);

                            methodVisitor.push(propertiesCommands.size());
                            methodVisitor.newInstance(arrayListType);
                            methodVisitor.dup();
                            methodVisitor.invokeConstructor(
                                    arrayListType,
                                    new Method(RenderUtils.CONSTRUCTOR_N, "(I)V")
                            );

                            for (ExpressionContext.RenderCommand propertyCommand : propertiesCommands) {
                                methodVisitor.dup();
                                propertyCommand.render(methodVisitor);
                                RenderUtils.renderMethodCall(methodVisitor, addMethod);
                            }
                        };
                    } else if (parameterClassElement.isArray()) {
                        if (parameterClassElement.getArrayDimensions() > 1) {
                            // Multi dimensional arrays are not supported, sorry.
                            return false;
                        }

                        ClassElement arrayClassElement = parameterClassElement.fromArray();

                        List<ExpressionContext.RenderCommand> propertiesCommands = properties.stream()
                                .<ExpressionContext.RenderCommand>map(property -> {
                                    ExpressionContext.RenderCommand loadCommand =
                                            property.loader.load(arrayClassElement).run();

                                    return methodVisitor -> {
                                        if (property.createCommand != null) {
                                            property.createCommand.render(methodVisitor);
                                        }

                                        loadCommand.render(methodVisitor);
                                    };
                                })
                                .collect(Collectors.toList());

                        renderCommand = methodVisitor -> {
                            Type parameterType = RenderUtils.type(arrayClassElement);

                            methodVisitor.push(propertiesCommands.size());
                            methodVisitor.newArray(parameterType);

                            ListIterator<ExpressionContext.RenderCommand> it = propertiesCommands.listIterator();

                            while (it.hasNext()) {
                                methodVisitor.dup();
                                methodVisitor.push(it.nextIndex());
                                it.next().render(methodVisitor);
                                methodVisitor.arrayStore(parameterType);
                            }
                        };
                    } else {
                        if (properties.size() > 1) {
                            // Multiple properties seen, but constructor accepts single instance. Refuse to render this.
                            return false;
                        }

                        SeenProperty property = properties.get(0);

                        ExpressionContext.RenderCommand loadCommand = property.loader.load(parameterClassElement).run();

                        renderCommand = methodVisitor -> {
                            if (property.createCommand != null) {
                                property.createCommand.render(methodVisitor);
                            }

                            loadCommand.render(methodVisitor);
                        };
                    }
                } else {
                    // We haven't seen this property. There might be default value set by NamedArg annotation. Pass null
                    //  if not.
                    String defaultValue = parameter.findDeclaredAnnotation(NamedArg.class)
                            .flatMap(v -> v.get("defaultValue", String.class))
                            .orElse(null);

                    if (defaultValue != null) {
                        renderCommand = RenderUtils.coerceOrThrow(context, defaultValue, parameter.getType());
                    } else {
                        if (ElementUtils.isPrimitive(parameterClassElement)) {
                            renderCommand = RenderUtils.coerceOrThrow(context, "0", parameterClassElement);
                        } else {
                            renderCommand = methodVisitor -> methodVisitor.push((Type) null);
                        }
                    }
                }

                toRender.add(renderCommand);
            }

            // Construct object.
            parent.render(methodVisitor -> {
                Type objectType = RenderUtils.type(classElement);
                methodVisitor.newInstance(objectType);
                methodVisitor.dup();

                toRender.forEach(c -> c.render(methodVisitor));

                String descriptor = Arrays.stream(constructorElement.getParameters())
                        .map(ParameterElement::getType)
                        .map(RenderUtils::type)
                        .map(Type::getDescriptor)
                        .collect(Collectors.joining("", "(", ")V"));

                methodVisitor.invokeConstructor(objectType, new Method(RenderUtils.CONSTRUCTOR_N, descriptor));
                methodVisitor.storeLocal(getSlot(), objectType);
            });

            // Render seen properties that are not constructor arguments as is.
            for (Map.Entry<String, List<SeenProperty>> entry : seenProperties.entrySet()) {
                if (constructorArguments.contains(entry.getKey())) {
                    continue;
                }

                List<SeenProperty> properties = entry.getValue();

                for (SeenProperty property : properties) {
                    if (property.createCommand != null) {
                        render(property.createCommand);
                    }
                    property.action.accept(context, property.loader);
                }
            }

            return true;
        }

        private void construct(CompilerContext context) {
            // Check for "exact match" logic first.
            for (ConstructorElement constructor : findConstructorsExactMatch()) {
                if (construct(context, constructor)) {
                    return;
                }
            }

            // Try to construct using no args constructor if all seen properties are RW.
            ConstructorElement noArgsConstructor = constructors.stream()
                    .filter(c -> c.getParameters().length == 0)
                    .findFirst()
                    .orElse(null);

            if (noArgsConstructor != null) {
                boolean allPropertiesAreRW = true;

                for (List<SeenProperty> properties : seenProperties.values()) {
                    for (SeenProperty property : properties) {
                        if (property.propertyElement.isReadOnly()) {
                            allPropertiesAreRW = false;
                            break;
                        }
                    }
                }

                if (allPropertiesAreRW) {
                    if (construct(context, noArgsConstructor)) {
                        return;
                    }
                }
            }

            // Try to find appropriate constructor.
            for (ConstructorElement constructor : findConstructorsForSeenProperties()) {
                if (construct(context, constructor)) {
                    return;
                }
            }

            throw context.compileError(
                    "Unable to find appropriate constructor to construct \"" + classElement.getName() + "\" class."
            );
        }

        void constructAndRender(CompilerContext context) {
            construct(context);

            if (!commands.isEmpty()) {
                parent.render(methodVisitor -> commands.forEach(c -> c.render(methodVisitor)));
            }

            if (!currentCommands.isEmpty()) {
                parent.render(methodVisitor -> currentCommands.forEach(c -> c.render(methodVisitor)));
            }

            context.setRenderer(parent);
        }
    }

    private ClassElement classElement;
    private ExpressionContext.RenderCommand constructCommand;
    private PropertyInterceptor propertyInterceptor;
    private Set<String> constructorArgNames = Collections.emptySet();

    public InstanceDeclarationFXMLElement(FXMLElement<?> parent, ClassElement classElement) {
        super(parent);

        this.classElement = classElement;
    }

    @Override
    public ClassElement getClassElement() {
        return classElement;
    }

    private void renderFxValue(CompilerContext context, String value) {
        CompilerContext.RenderCommand command = RenderUtils.coerceOrThrow(context, value, classElement);

        setConstructCommand(context, methodVisitor -> {
            command.render(methodVisitor);

            Type objectType = RenderUtils.type(classElement);
            methodVisitor.storeLocal(getSlot(), objectType);
        });
    }

    private Class<?> primitiveToWrapper(ClassElement type) {
        assert ElementUtils.isPrimitive(type);

        Class<?> wrapper = null;

        for (Map.Entry<Class<?>, PrimitiveElement> entry : RenderUtils.BOXED_PRIMITIVES.entrySet()) {
            if (type.isAssignable(entry.getValue())) {
                wrapper = entry.getKey();
                break;
            }
        }

        assert wrapper != null;
        return wrapper;
    }

    private void renderFxConstant(CompilerContext context, String value) {
        // NB: Also query for enum values, as FXMLLoaders treats enum values as constants.
        TypedElement field = classElement.getEnclosedElement(
                        ((ElementQuery<? extends TypedElement>) ElementQuery.ALL_FIELDS)
                                .filter(e -> e.getModifiers().contains(ElementModifier.STATIC))
                                .onlyAccessible()
                                .includeEnumConstants()
                                .named(Predicate.isEqual(value))
                )
                .orElseThrow(() -> context.compileError(
                        "Unable to find static field \"" + value + "\" in class \"" + classElement + "\".")
                );

        ClassElement fieldType = field.getType();

        // NB: Cast to wrapper is required if constant is primitive, and it is root element, as generated loader always
        //  returns objects.
        Class<?> wrapperClass = getParent() == null && ElementUtils.isPrimitive(fieldType)
                ? primitiveToWrapper(fieldType)
                : null;

        // Save old class type, as class element will be overridden, and renderer will not render immediately.
        Type oldClassType = RenderUtils.type(classElement);

        setConstructCommand(context, methodVisitor -> {
            Type fieldObjectType = RenderUtils.type(fieldType);

            methodVisitor.getStatic(
                    oldClassType,
                    field.getName(),
                    fieldObjectType
            );

            if (wrapperClass != null) {
                methodVisitor.valueOf(fieldObjectType);
            }

            methodVisitor.storeLocal(
                    getSlot(),
                    wrapperClass != null
                            ? Type.getType(wrapperClass)
                            : fieldObjectType
            );
        });

        classElement = wrapperClass != null
                ? context.getClassElement(wrapperClass)
                : fieldType;
    }

    private void renderFxFactory(CompilerContext context, String value) {
        // NB: Value means method name in this context.
        MethodElement factory = classElement.getEnclosedElement(
                        ElementQuery.ALL_METHODS
                                .modifiers(m -> m.contains(ElementModifier.STATIC))
                                .onlyAccessible()
                                .named(Predicate.isEqual(value))
                                .filter(m -> m.getParameters().length == 0)
                )
                .orElseThrow(() -> context.compileError(
                        "Unable fo find factory method \"" + value + "\" in class \"" + classElement + "\"."
                ));

        ClassElement factoryType = factory.getReturnType();

        // NB: Cast to wrapper is required if factory return type is primitive, and it is root element, as generated
        //  loader always returns objects.
        Class<?> wrapperClass = getParent() == null && ElementUtils.isPrimitive(factoryType)
                ? primitiveToWrapper(factoryType)
                : null;

        // Save old class type, as class element will be overridden, and renderer will not render immediately.
        Type oldClassType = RenderUtils.type(classElement);

        setConstructCommand(context, methodVisitor -> {
            Type factoryObjectType = RenderUtils.type(factoryType);

            methodVisitor.invokeStatic(
                    oldClassType,
                    new Method(value, "()" + factoryObjectType.getDescriptor())
            );

            if (wrapperClass != null) {
                methodVisitor.valueOf(factoryObjectType);
            }

            methodVisitor.storeLocal(
                    getSlot(),
                    wrapperClass != null
                            ? Type.getType(wrapperClass)
                            : factoryObjectType
            );
        });

        classElement = wrapperClass != null
                ? context.getClassElement(wrapperClass)
                : factoryType;
    }

    private void renderDefaultConstructor(CompilerContext context) {
        context.getRenderer().render(methodVisitor -> {
            Type objectType = RenderUtils.type(classElement);
            methodVisitor.newInstance(objectType);
            methodVisitor.dup();
            methodVisitor.invokeConstructor(objectType, new Method(RenderUtils.CONSTRUCTOR_N, "()V"));
            methodVisitor.storeLocal(getSlot(), objectType);
        });
    }

    private void setConstructCommand(CompilerContext context, ExpressionContext.RenderCommand renderCommand) {
        if (this.constructCommand != null) {
            throw context.compileError(
                    "Only one of fx:value, fx:constant, or fx:factory property can be set."
            );
        }

        this.constructCommand = renderCommand;
    }

    @Override
    public boolean requiresAttributesLookahead() {
        // NB: We use attributes lookahead here, as there might be fx:factory or fx:constant attribute set. fx:factory
        //  and fx:constant changes element semantics. When one of those is specified, actual element type is the target
        //  type (a type returned by factory method or a type of constant loaded) not the type specified in element's
        //  constructor.
        return true;
    }

    @Override
    public void handleAttributeLookahead(CompilerContext context, String prefix, String localName, String value) {
        // Only interested in fx namespace attributes
        if (!FXMLLoader.FX_NAMESPACE_PREFIX.equals(prefix)) {
            if (super.requiresAttributesLookahead()) {
                super.handleAttributeLookahead(context, prefix, localName, value);
            }
            return;
        }

        // NB: We cannot render inside this method, as this will cause rendering to uninitialized root renderer if it is
        //  root element of document. So, use delegating renderer instead.
        switch (localName) {
            case FXMLLoader.FX_VALUE_ATTRIBUTE:
                renderFxValue(context, value);
                break;
            case FXMLLoader.FX_CONSTANT_ATTRIBUTE:
                renderFxConstant(context, value);
                break;
            case FXMLLoader.FX_FACTORY_ATTRIBUTE:
                renderFxFactory(context, value);
                break;
            default:
                // Unknown attribute. Delegate to superclass.
                if (super.requiresAttributesLookahead()) {
                    super.handleAttributeLookahead(context, prefix, localName, value);
                }
        }
    }

    @Override
    public void handleAttributesLookaheadFinish(CompilerContext context) {
        if (constructCommand != null) {
            context.getRenderer().render(constructCommand);
            constructCommand = null;
        } else {
            List<ConstructorElement> constructors = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS
                    .onlyAccessible()
                    .filter(c -> Arrays.stream(c.getParameters())
                            .allMatch(p -> p.hasDeclaredAnnotation(NamedArg.class))
                    )
            );

            if (constructors.isEmpty()) {
                throw context.compileError(
                        "No applicable accessible constructors available in class \"" + classElement.getName() + "\"."
                );
            }

            if (constructors.size() == 1 && constructors.get(0).getParameters().length == 0) {
                // No constructors with named args. Render default constructor now and forget.
                renderDefaultConstructor(context);
            } else {
                // We need to use delegating renderer here to collect all properties set to this object to choose the
                //  best constructor available.
                constructorArgNames = constructors.stream()
                        .flatMap(c -> Arrays.stream(c.getParameters()))
                        .map(p -> p.findDeclaredAnnotation(NamedArg.class)
                                .flatMap(v -> v.getValue(String.class))
                                .orElseThrow(AssertionError::new)
                        )
                        .collect(Collectors.toSet());
                propertyInterceptor = new PropertyInterceptor(context, constructors);
            }
        }

        if (super.requiresAttributesLookahead()) {
            super.handleAttributesLookaheadFinish(context);
        }
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        // Only interested in fx namespace attributes
        if (!FXMLLoader.FX_NAMESPACE_PREFIX.equals(prefix)) {
            super.handleAttribute(context, prefix, localName, value);
            return;
        }

        switch (localName) {
            case FXMLLoader.FX_VALUE_ATTRIBUTE:
            case FXMLLoader.FX_CONSTANT_ATTRIBUTE:
            case FXMLLoader.FX_FACTORY_ATTRIBUTE:
                return;
            default:
                // Unknown attribute. Delegate to superclass.
                super.handleAttribute(context, prefix, localName, value);
        }
    }

    @Override
    public void handleEndElement(CompilerContext context) {
        //NB: Construct this object before notifying parent about us.
        if (propertyInterceptor != null) {
            propertyInterceptor.constructAndRender(context);
        }

        super.handleEndElement(context);
    }

    @Override
    public CompilerContext.RenderCommand load() {
        // TODO: It wold be nice not to store primitives to variables, and use constants where needed.
        return methodVisitor -> methodVisitor.loadLocal(getSlot(), RenderUtils.type(getClassElement()));
    }

    @Override
    void apply(CompilerContext context, PropertyElement property, ValueLoader loader) {
        if (propertyInterceptor != null) {
            propertyInterceptor.intercept(context, property, loader);
            return;
        }

        super.apply(context, property, loader);
    }

    @Override
    void apply(CompilerContext context, ReadOnlyInstanceProperty property, ValueLoader loader) {
        if (propertyInterceptor != null) {
            propertyInterceptor.intercept(context, property, loader);
            return;
        }

        super.apply(context, property, loader);
    }

    @Override
    void apply(CompilerContext context, ReadWriteInstanceProperty property, ValueLoader loader) {
        if (propertyInterceptor != null) {
            propertyInterceptor.intercept(context, property, loader);
            return;
        }

        super.apply(context, property, loader);
    }

    @Override
    void apply(CompilerContext context, IdentifiableFXMLElement element) {
        if (propertyInterceptor != null) {
            propertyInterceptor.intercept(context, element);
            return;
        }

        super.apply(context, element);
    }

    @Override
    void apply(CompilerContext context, ClassElement classElement, String name, ValueLoader valueLoader) {
        if (propertyInterceptor != null) {
            propertyInterceptor.intercept(context, classElement, name, valueLoader);
            return;
        }

        super.apply(context, classElement, name, valueLoader);
    }

    @Override
    public Optional<PropertyElement> findProperty(String name) {
        Optional<PropertyElement> property = super.findProperty(name);
        if (property.isPresent()) {
            return property;
        }

        if (constructorArgNames.contains(name)) {
            ParameterElement parameterElement = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS
                    .onlyAccessible()
                    .filter(c -> Arrays.stream(c.getParameters())
                            .allMatch(p -> p.hasDeclaredAnnotation(NamedArg.class))
                    )
            ).stream()
                    .flatMap(c -> Arrays.stream(c.getParameters()))
                    .filter(p -> {
                        String propertyName = p.findDeclaredAnnotation(NamedArg.class)
                                .flatMap(v -> v.getValue(String.class))
                                .orElseThrow(AssertionError::new);

                        return name.equals(propertyName);
                    })
                    .findFirst()
                    .orElseThrow(AssertionError::new);

            return Optional.of(new PropertyElement() {

                @NonNull
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public boolean isProtected() {
                    return false;
                }

                @Override
                public boolean isPublic() {
                    return true;
                }

                @NonNull
                @Override
                public Object getNativeType() {
                    return parameterElement.getNativeType();
                }

                @Override
                public ClassElement getDeclaringType() {
                    return classElement.getType();
                }

                @NonNull
                @Override
                public ClassElement getType() {
                    return parameterElement.getType();
                }
            });
        }

        return Optional.empty();
    }
}
