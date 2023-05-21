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

import io.github.paullo612.mlfx.api.CompiledLoadException;
import io.github.paullo612.mlfx.api.Result;
import io.github.paullo612.mlfx.compiler.CompileTask;
import io.github.paullo612.mlfx.compiler.CompilerContext;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.inject.ast.ClassElement;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class IncludeFXMLElement extends IdentifiableFXMLElement {

    private String source;
    private String resources;
    private Charset charset;
    private CompileTask.CompiledFXMLLoaderReference reference;

    public IncludeFXMLElement(FXMLElement<?> parent) {
        super(parent);
    }

    @Override
    public boolean requiresAttributesLookahead() {
        // NB: We use attributes lookahead here, as actual element type can only be determined from attribute.
        return true;
    }

    @Override
    public void handleAttributeLookahead(CompilerContext context, String prefix, String localName, String value) {
        // Only interested in non-prefixed attributes
        if (prefix != null && !prefix.isEmpty()) {
            if (super.requiresAttributesLookahead()) {
                super.handleAttributeLookahead(context, prefix, localName, value);
            }
            return;
        }

        switch (localName) {
            case FXMLLoader.INCLUDE_SOURCE_ATTRIBUTE:
                this.source = value;
                break;
            case FXMLLoader.INCLUDE_RESOURCES_ATTRIBUTE:
                this.resources = value;
                break;
            case FXMLLoader.INCLUDE_CHARSET_ATTRIBUTE:
                try {
                    charset = Charset.forName(value);
                } catch (IllegalCharsetNameException e) {
                    throw context.compileError("Failed to parse charset name \"" + value + "\".");
                } catch (UnsupportedCharsetException e) {
                    throw context.compileError("Charset \"" + value + "\" is not supported.");
                }
            default:
                // Unknown attribute. Delegate to superclass.
                if (super.requiresAttributesLookahead()) {
                    super.handleAttributeLookahead(context, prefix, localName, value);
                }
        }
    }

    @Override
    public void handleAttributesLookaheadFinish(CompilerContext context) {
        if (source == null) {
            throw context.compileError(FXMLLoader.INCLUDE_SOURCE_ATTRIBUTE + " attribute is required.");
        }

        URI location;

        try {
            location = new URI(source);
        } catch (URISyntaxException e) {
            throw context.compileError("Unable to parse source attribute value: " + e.getMessage());
        }

        CompileTask compileTask = context.getCompileTask(location)
                .orElseThrow(() -> context.compileError("Source file " + location + " not found."));

        reference = compileTask.compile(charset != null ? charset : context.getCharset());
        acquireSlot(context);

        ExpressionContext.Loadable controllerAccessorFactory = context.getControllerAccessorFactory();

        context.getRenderer().render(methodVisitor -> {
            Type targetType = RenderUtils.type(reference.getTargetType());

            methodVisitor.newInstance(targetType);
            methodVisitor.dup();

            methodVisitor.invokeConstructor(targetType, new Method(RenderUtils.CONSTRUCTOR_N, "()V"));

            // Ours controller accessor factory should do.
            controllerAccessorFactory.load().render(methodVisitor);

            // No external controller
            methodVisitor.push((Type) null);

            // No external root instance
            methodVisitor.push((Type) null);

            Type resourceBundleType = Type.getType(ResourceBundle.class);

            if (resources != null) {
                // Load resource bundle, if specified
                //
                // try {
                //     ResourceBundle.getBundle(resources);
                // } catch (MissingResourceException e) {
                //     throw new CompiledLoadException(e);
                // }
                Label start = methodVisitor.mark();

                methodVisitor.invokeStatic(
                        resourceBundleType,
                        new Method("getBundle", "(" + RenderUtils.STRING_D + ")" + resourceBundleType.getDescriptor())
                );
                Label next = methodVisitor.newLabel();
                methodVisitor.goTo(next);

                Label end = methodVisitor.mark();

                Type exceptionType = Type.getType(MissingResourceException.class);
                methodVisitor.catchException(start, end, exceptionType);

                // Local variable slot index does not matter here. We'll throw anyway.
                methodVisitor.storeLocal(context.getDefaultSlot(), exceptionType);
                Type loadExceptionType = Type.getType(CompiledLoadException.class);
                methodVisitor.newInstance(loadExceptionType);
                methodVisitor.dup();
                methodVisitor.loadLocal(context.getDefaultSlot());
                methodVisitor.invokeConstructor(
                        loadExceptionType,
                        new Method(
                                RenderUtils.CONSTRUCTOR_N, "(" + Type.getType(Throwable.class).getDescriptor() + ")V"
                        )
                );
                methodVisitor.throwException();

                methodVisitor.mark(next);
            } else {
                // No resources specified, ours resource bundle should do.
                context.getNonRequiredResourceBundle().load().render(methodVisitor);
            }

            Type resultType = Type.getType(Result.class);

            methodVisitor.invokeVirtual(
                    targetType,
                    new Method(
                            "load",
                            "(" + RenderUtils.type(controllerAccessorFactory.getClassElement())
                                    + RenderUtils.OBJECT_D
                                    + RenderUtils.OBJECT_D
                                    + resourceBundleType.getDescriptor()
                                    + ")"
                                    + resultType.getDescriptor()
                    )
            );

            methodVisitor.storeLocal(getSlot(), resultType);
        });
    }

    @Override
    void applyFxId(CompilerContext context, String id) {
        super.applyFxId(context, id);

        String controllerId = id + FXMLLoader.CONTROLLER_SUFFIX;

        ExpressionContext.Loadable controllerLoadable = new ExpressionContext.Loadable() {

            @Override
            public ClassElement getClassElement() {
                return reference.getControllerClassElement();
            }

            @Override
            public ExpressionContext.RenderCommand load() {
                return methodVisitor -> {
                    methodVisitor.loadLocal(getSlot());

                    Type resultType = Type.getType(Result.class);
                    methodVisitor.invokeInterface(
                            resultType,
                            new Method(
                                    "getController",
                                    "()" + RenderUtils.OBJECT_D
                            )
                    );
                    methodVisitor.checkCast(RenderUtils.type(getClassElement()));
                };
            }
        };

        context.getScope().put(controllerId, controllerLoadable);
        context.setControllerField(controllerId, controllerLoadable);
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        // Only interested in non-prefixed attributes
        if (prefix != null && !prefix.isEmpty()) {
            super.handleAttribute(context, prefix, localName, value);
            return;
        }

        switch (localName) {
            case FXMLLoader.INCLUDE_SOURCE_ATTRIBUTE:
            case FXMLLoader.INCLUDE_RESOURCES_ATTRIBUTE:
            case FXMLLoader.INCLUDE_CHARSET_ATTRIBUTE:
                return;
            default:
                super.handleAttribute(context, prefix, localName, value);
        }
    }

    @Override
    public CompilerContext.RenderCommand load() {
        return methodVisitor -> {
            methodVisitor.loadLocal(getSlot());

            Type resultType = Type.getType(Result.class);
            methodVisitor.invokeInterface(
                    resultType,
                    new Method(
                            "getRootInstance",
                            "()" + RenderUtils.OBJECT_D
                    )
            );
            methodVisitor.checkCast(RenderUtils.type(reference.getRootClassElement()));
        };
    }

    @Override
    public ClassElement getClassElement() {
        return reference != null ? reference.getRootClassElement() : null;
    }
}
