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
import io.github.paullo612.mlfx.compiler.CompilerContext;
import io.github.paullo612.mlfx.expression.Continuation;
import io.github.paullo612.mlfx.expression.Expressions;
import io.micronaut.inject.ast.ClassElement;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;

// NB: Applies to both static and ind instance properties.
class PropertyValueLoader implements LoadableFXMLElement.ValueLoader {

    private final CompilerContext context;
    private final String value;

    public PropertyValueLoader(CompilerContext context, String value) {
        this.context = context;
        this.value = value;
    }

    private boolean checkDeprecatedEscapeSequence(String value, String prefix) {
        if (value.startsWith(prefix)) {
            context.warn(prefix + prefix + " is a deprecated escape sequence. Please use \\" + prefix + " instead.");
            return true;
        }

        return false;
    }

    private Continuation loadPrefixedValue(ClassElement targetType, String value) {
        if (value.startsWith(FXMLLoader.RELATIVE_PATH_PREFIX)) {
            value = value.substring(FXMLLoader.RELATIVE_PATH_PREFIX.length());
            if (value.length() == 0) {
                throw context.compileError("Missing relative path.");
            }

            if (checkDeprecatedEscapeSequence(value, FXMLLoader.RELATIVE_PATH_PREFIX)) {
                return null;
            }

            if (value.charAt(0) == '/') {
                // NB: This path is broken for modularized applications. It does not take in account resource's module
                //  name. FXMLLoader is broken either. Stay complaint with FXMLLoader.

                // Absolute classpath URL.
                String pathString = value.substring(1);
                String exceptionMessage = "Invalid resource: " + value + " not found on the classpath";

                // URL resource = getClass().getClassLoader().getResource(pathString);
                // if (resource == null) {
                //     throw new CompiledLoadException(exceptionMessage);
                // }
                // resource.toString();
                return () -> methodVisitor -> {
                    methodVisitor.loadThis();

                    Type classType = Type.getType(Class.class);
                    Type classLoaderType = Type.getType(ClassLoader.class);
                    Type urlType = Type.getType(URL.class);

                    methodVisitor.invokeVirtual(
                            Type.getType(Object.class),
                            new Method("getClass", "()" + classType.getDescriptor())
                    );
                    methodVisitor.invokeVirtual(
                            classType,
                            new Method("getClassLoader", "()" + classLoaderType.getDescriptor())
                    );
                    methodVisitor.push(pathString);
                    methodVisitor.invokeVirtual(
                            classLoaderType,
                            new Method("getResource", "(" + RenderUtils.STRING_D + ")" + urlType.getDescriptor())
                    );

                    Label label = methodVisitor.newLabel();
                    methodVisitor.dup();
                    methodVisitor.ifNonNull(label);
                    methodVisitor.throwException(Type.getType(CompiledLoadException.class), exceptionMessage);
                    methodVisitor.mark(label);
                    methodVisitor.invokeVirtual(
                            urlType,
                            new Method(RenderUtils.TO_STRING_M, "()" + RenderUtils.STRING_D)
                    );
                };
            }

            String pathString = value;

            CompilerContext.RenderCommand loadLocationCommand =
                    context.getScope().get(FXMLLoader.LOCATION_KEY).load();

            // URL resource = getClass().getResource(fxmlFileName);
            // if (resource == null) {
            //     throw new CompiledLoadException(exceptionMessage);
            // }
            // try {
            //     new URL(resource, pathString).toString();
            // } catch (MalformedURLException e) {
            //     throw new CompiledLoadException(e);
            // }
            return () -> methodVisitor -> {
                Label start = methodVisitor.mark();

                Type urlType = Type.getType(URL.class);

                methodVisitor.newInstance(urlType);
                methodVisitor.dup();

                // Expands to
                // URL resource = getClass().getResource(fxmlFileName);
                // if (resource == null) {
                //     throw new CompiledLoadException(exceptionMessage);
                // }
                loadLocationCommand.render(methodVisitor);

                methodVisitor.push(pathString);
                methodVisitor.invokeConstructor(
                        urlType,
                        new Method(
                                RenderUtils.CONSTRUCTOR_N, "(" + urlType.getDescriptor() + RenderUtils.STRING_D + ")V"
                        )
                );
                methodVisitor.invokeVirtual(urlType, new Method(RenderUtils.TO_STRING_M, "()" + RenderUtils.STRING_D));
                Label next = methodVisitor.newLabel();
                methodVisitor.goTo(next);

                Label end = methodVisitor.mark();

                Type exceptionType = Type.getType(MalformedURLException.class);
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
            };
        } else if (value.startsWith(FXMLLoader.RESOURCE_KEY_PREFIX)) {
            value = value.substring(FXMLLoader.RESOURCE_KEY_PREFIX.length());
            if (value.length() == 0) {
                throw context.compileError("Missing resource key.");
            }

            if (checkDeprecatedEscapeSequence(value, FXMLLoader.RESOURCE_KEY_PREFIX)) {
                return null;
            }

            String resourceString = value;
            String exceptionMessage = "Resource \"" + value + "\" not found.";

            CompilerContext.RenderCommand loadResourcesCommand =
                    context.getScope().get(FXMLLoader.RESOURCES_KEY).load();

            // if (!resources.containsKey(aValue)) {
            //     throw new CompiledLoadException(exceptionMessage);
            // }
            //
            // resources.getString(aValue);
            return () -> methodVisitor -> {
                Type resourceBundleType = Type.getType(ResourceBundle.class);

                loadResourcesCommand.render(methodVisitor);
                methodVisitor.dup();
                methodVisitor.push(resourceString);
                methodVisitor.invokeVirtual(
                        resourceBundleType, new Method("containsKey", "(" + RenderUtils.STRING_D + ")Z")
                );

                Label label = methodVisitor.newLabel();
                methodVisitor.ifZCmp(GeneratorAdapter.NE, label);
                methodVisitor.throwException(Type.getType(CompiledLoadException.class), exceptionMessage);
                methodVisitor.mark(label);
                methodVisitor.push(resourceString);
                methodVisitor.invokeVirtual(
                        resourceBundleType,
                        new Method("getString", "(" + RenderUtils.STRING_D + ")" + RenderUtils.STRING_D)
                );
            };
        } else if (value.startsWith(FXMLLoader.EXPRESSION_PREFIX)) {
            value = value.substring(FXMLLoader.EXPRESSION_PREFIX.length());
            if (value.length() == 0) {
                throw context.compileError("Missing expression.");
            }

            if (checkDeprecatedEscapeSequence(value, FXMLLoader.EXPRESSION_PREFIX)) {
                return null;
            }

            return Expressions.expression(context, value, targetType);
        }
        return null;
    }

    private String unescape(String value) {
        value = value.substring(FXMLLoader.ESCAPE_PREFIX.length());

        if (value.length() == 0
                || !(value.startsWith(FXMLLoader.ESCAPE_PREFIX)
                || value.startsWith(FXMLLoader.RELATIVE_PATH_PREFIX)
                || value.startsWith(FXMLLoader.RESOURCE_KEY_PREFIX)
                || value.startsWith(FXMLLoader.EXPRESSION_PREFIX)
                || value.startsWith(FXMLLoader.BI_DIRECTIONAL_BINDING_PREFIX))) {
            throw context.compileError("Invalid escape sequence.");
        }

        return value;
    }

    private Continuation unescapeAndLoad(ClassElement targetType, String value) {
        Continuation continuation = null;

        if (value.startsWith(FXMLLoader.ESCAPE_PREFIX)) {
            value = unescape(value);
        } else {
            // Parse prefixes only if value is not escaped
            continuation = loadPrefixedValue(targetType, value);
        }

        if (continuation == null) {
            String finalValue = value;

            // There is no prefixes. Coerce value if needed.
            CompilerContext.RenderCommand command =
                    ElementUtils.isAssignable(context.getClassElement(String.class), targetType)
                            ? methodVisitor -> methodVisitor.push(finalValue)
                            : RenderUtils.coerceOrThrow(context, finalValue, targetType);

            continuation = () -> command;
        }

        return continuation;
    }

    private List<Continuation> loadList(ClassElement targetType) {
        if (value.isEmpty()) {
            return Collections.emptyList();
        }

        // TODO: Is this correct? It may fail if it is a list of a binding expressions (may contain ','), or a relative
        //  path with ',' in it (is it possible?).
        String[] splitValues = value.split(FXMLLoader.ARRAY_COMPONENT_DELIMITER);
        List<Continuation> continuations = new ArrayList<>(splitValues.length);

        for (String splitValue : splitValues) {
            continuations.add(unescapeAndLoad(targetType, splitValue.trim()));
        }

        return continuations;
    }

    @Override
    public List<Continuation> loadReadOnlyList(ClassElement targetType) {
        return loadList(targetType);
    }

    @Override
    public Continuation load(ClassElement targetType) {
        Continuation continuation = null;

        if (targetType.isArray()) {
            // Construct array of split values
            List<Continuation> list = loadList(targetType);

            continuation = new Continuation() {

                @Override
                public boolean canBeRun() {
                    // Check if all values can be applied immediately.
                    return list.stream()
                            .allMatch(Continuation::canBeRun);
                }

                @Override
                public CompilerContext.RenderCommand run() {
                    List<CompilerContext.RenderCommand> commands = new ArrayList<>(list.size());

                    for (Continuation listContinuation : list) {
                        commands.add(listContinuation.run());
                    }

                    return methodVisitor -> {
                        Type arrayType = RenderUtils.type(targetType.fromArray());

                        methodVisitor.push(list.size());
                        methodVisitor.newArray(arrayType);

                        ListIterator<CompilerContext.RenderCommand> it = commands.listIterator();

                        while (it.hasNext()) {
                            methodVisitor.dup();
                            methodVisitor.push(it.nextIndex());
                            CompilerContext.RenderCommand listCommand = it.next();
                            listCommand.render(methodVisitor);
                            methodVisitor.arrayStore(arrayType);
                        }
                    };
                }
            };
        }

        if (continuation == null) {
            continuation = unescapeAndLoad(targetType, value);
        }

        return continuation;
    }
}
