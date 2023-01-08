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

import com.sun.javafx.beans.IDProperty;
import io.github.paullo612.mlfx.compiler.CompilerContext;
import io.github.paullo612.mlfx.compiler.ProcessingInstructions;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import javafx.fxml.FXMLLoader;

import java.util.Map;

// FXML element that can be identified. Can have id assigned, so, can be referenced anywhere in FXML file.
abstract class IdentifiableFXMLElement extends LoadableFXMLElement<FXMLElement<?>> {

    private int slot;
    private boolean hasId;
    private String value;

    IdentifiableFXMLElement(FXMLElement<?> parent) {
        super(parent);
    }

    @Override
    public void initialize(CompilerContext context) {
        slot = context.acquireSlot();
    }

    @Override
    public void handleAttributeLookahead(CompilerContext context, String prefix, String localName, String value) {
        if (!FXMLLoader.FX_NAMESPACE_PREFIX.equals(prefix)
                || !FXMLLoader.CONTROLLER_KEYWORD.equals(localName)
                || getParent() != null) {
            return;
        }

        Map<String, ExpressionContext.Loadable> scope = context.getScope();

        if (scope.containsKey(FXMLLoader.CONTROLLER_KEYWORD)) {
            throw context.compileError(
                    "Document's root node defines " + FXMLLoader.FX_NAMESPACE_PREFIX + ":"
                            + FXMLLoader.FX_CONTROLLER_ATTRIBUTE
                            + " attribute, but external controller is already requested by "
                            + ProcessingInstructions.MLFX_CONTROLLER_TYPE
                            + " processing instruction."
            );
        }

        ClassElement controllerClass = context.getFXMLClassElement(value)
                .orElseThrow(() -> context.compileError("Invalid type \"" + value + "\"."));

        scope.put(FXMLLoader.CONTROLLER_KEYWORD, context.declareFxController(controllerClass));
    }

    private void doSetIdProperty(CompilerContext context, String id, MethodElement setter) {
        ClassElement parameterElement = setter.getParameters()[0].getType();

        ExpressionContext.RenderCommand renderCommand =
                ElementUtils.isAssignable(parameterElement, CharSequence.class)
                        ? methodVisitor -> methodVisitor.push(id)
                        : RenderUtils.coerceOrThrow(context, id, parameterElement);

        context.getRenderer().render(v -> renderPropertySetter(v, renderCommand, setter));
    }

    private void setIdProperty(CompilerContext context, String id) {
        ClassElement classElement = getClassElement();

        AnnotationValue<IDProperty> annotation = classElement.getAnnotation(IDProperty.class);

        if (annotation == null) {
            // No ID property present, noting to do here.
            return;
        }

        String propertyName = annotation.getRequiredValue(String.class);

        findProperty(propertyName)
                .flatMap(PropertyElement::getWriteMethod)
                .ifPresent(setter -> doSetIdProperty(context, id, setter));
    }

    void applyFxId(CompilerContext context, String id) {
        context.getScope().put(id, this);
        setIdProperty(context, id);
        context.setControllerField(id, this);
    }

    private void handleFxAttribute(CompilerContext context, String localName, String value) {
        switch (localName) {
            case FXMLLoader.FX_ID_ATTRIBUTE: {
                // NB: It would be nice to check identifier against of all reserved words of target language, but what
                //  language our controller is written on? We can be fed with controller written in Kotlin, for example.
                //  So, compile time check cannot be applied here. Just do the same checks as FXMLLoader does.
                if (value.equals(FXMLLoader.NULL_KEYWORD)) {
                    throw context.compileError("Invalid identifier.");
                }

                for (int i = 0; i < value.length(); ++i) {
                    if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                        throw context.compileError("Invalid identifier.");
                    }
                }

                applyFxId(context, value);

                hasId = true;
                break;
            }
            case FXMLLoader.CONTROLLER_KEYWORD: {
                if (getParent() != null) {
                    throw context.compileError(
                            FXMLLoader.FX_NAMESPACE_PREFIX + ":" + FXMLLoader.FX_CONTROLLER_ATTRIBUTE
                                    + " can only be applied to root element."
                    );
                }
                break;
            }
            default:
                throw context.compileError("Invalid attribute.");
        }
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        if (FXMLLoader.FX_NAMESPACE_PREFIX.equals(prefix)) {
            handleFxAttribute(context, localName, value);
            return;
        }

        super.handleAttribute(context, prefix, localName, value);
    }

    @Override
    public boolean requiresAttributesLookahead() {
        // NB: We could actually request lookahead here, but there is no sense in this. Instance attribute expressions
        //  can reference ours fx:id, but those will be transformed in non-runnable continuations if id is missing,
        //  which will be run later, after full children traversal. Non-runnable continuation may require additional
        //  variable slot, but this slot will be occupied anyway, as if element has id, it must be passed to controller
        //  fields injection logic. Require lookahead only if it is root node of document, as fx:controller might be
        //  specified there.
        return getParent() == null;
    }

    @Override
    void apply(CompilerContext context, IdentifiableFXMLElement element) {
        // NB: Try to add new element to collection if this element is a collection first. Delegate to @DefaultProperty
        //  handling logic if it is not.
        handleReadOnlyInstancePropertyAttribute(context, getClassElement(), loadElement(context, element));
    }

    @Override
    public void handleEndElement(CompilerContext context) {
        try {
            super.handleEndElement(context);

            // We have characters inside. Set default string property.
            if (value != null) {
                ClassElement classElement = getClassElement();

                PropertyElement propertyElement = findDefaultProperty(context, classElement)
                        .orElseThrow(() -> context.compileError(
                                        "No default property specified in class \""
                                                + ElementUtils.getSimpleName(classElement) + "\"."
                                )
                        );

                applyInstanceProperty(context, classElement, propertyElement, loadString(context, value));

                return;
            }

            // Handle parent's default property.
            FXMLElement<?> parent = getParent();
            if (parent != null) {
                parent.apply(context, this);
            }
        } finally {
            if (!hasId) {
                context.releaseSlot(slot);
            }
        }
    }

    int getSlot() {
        return slot;
    }

    @Override
    public void handleCharacters(CompilerContext context, String text) {
        value = MULTI_WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }
}
