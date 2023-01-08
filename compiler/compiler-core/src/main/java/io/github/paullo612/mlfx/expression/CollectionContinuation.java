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
package io.github.paullo612.mlfx.expression;

import io.github.paullo612.mlfx.compiler.elements.ElementUtils;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PrimitiveElement;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

class CollectionContinuation extends AbstractNamedContinuation {

    static final String NULL_TYPE_ERROR = "Load by index cannot be applied to null.";
    static final String INDEX_NULL_TYPE_ERROR = "null cannot be used as index.";
    static final String INDEX_BOOLEAN_TYPE_ERROR = "boolean literal cannot be used as index.";
    static final String STRING_INDEX_ERROR_FORMAT = "Illegal string literal \"%1$s\" used as load index.";

    static final String OUT_OF_BOUNDS_ERROR = "Load index is out of bounds.";

    static final String ILLEGAL_INDEX_ERROR_FORMAT = "Value %1$s cannot be used as index.";

    static LiteralVisitor<String> previousLiteralVisitor(ExpressionContext context) {
        return new LiteralVisitor<>() {

            @Override
            public String visit(StringLiteral literal) {
                return literal.getValue();
            }

            @Override
            public String visit(NullLiteral literal) {
                throw context.compileError(NULL_TYPE_ERROR);
            }

            @Override
            public String visit(DecimalLiteral literal) {
                return String.valueOf(literal.getValue());
            }

            @Override
            public String visit(FloatingPointLiteral literal) {
                return String.valueOf(literal.getValue());
            }

            @Override
            public String visit(BooleanLiteral literal) {
                return String.valueOf(literal.getValue());
            }
        };
    }

    static LiteralVisitor<Integer> argumentLiteralVisitor(ExpressionContext context) {
        return new LiteralVisitor<>() {

            @Override
            public Integer visit(StringLiteral literal) {
                try {
                    return Integer.parseInt(literal.getValue());
                } catch (NumberFormatException e) {
                    // ignore
                }

                throw context.compileError(String.format(STRING_INDEX_ERROR_FORMAT, literal.getValue()));
            }

            @Override
            public Integer visit(DecimalLiteral literal) {
                return asIndex(context, literal.getValue());
            }

            @Override
            public Integer visit(FloatingPointLiteral literal) {
                return asIndex(context, literal.getValue());
            }

            @Override
            public Integer visit(BooleanLiteral literal) {
                throw context.compileError(INDEX_BOOLEAN_TYPE_ERROR);
            }

            @Override
            public Integer visit(NullLiteral literal) {
                throw context.compileError(INDEX_NULL_TYPE_ERROR);
            }
        };
    }

    static String loadCharacter(ExpressionContext context, String string, int index) {
        if (index >= string.length()) {
            throw context.compileError(OUT_OF_BOUNDS_ERROR);
        }

        return String.valueOf(string.charAt(index));
    }

    private static int asIndex(ExpressionContext context, long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw context.compileError(String.format(ILLEGAL_INDEX_ERROR_FORMAT, value));
        }

        return (int) value;
    }

    private static int asIndex(ExpressionContext context, double value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw context.compileError(String.format(ILLEGAL_INDEX_ERROR_FORMAT, (long) value));
        }

        return (int) value;
    }

    class ListGetterContinuation implements ExpressionContinuation {

        private final ClassElement classElement;

        ListGetterContinuation() {
            this.classElement = previous.getClassElement().getFirstTypeArgument()
                    .filter(e -> !e.isGenericPlaceholder())
                    .orElseGet(() -> context.getClassElement(Object.class));
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassElement getClassElement() {
            return classElement;
        }

        @Override
        public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
            ExpressionContext.RenderCommand argumentCommand;

            if (!ElementUtils.isAssignable(argument.getClassElement(), int.class)) {
                argumentCommand = argument.asLiteral()
                        .map(CollectionContinuation.this::literalToIndexRenderCommand)
                        // Do runtime coercion if it is not a literal.
                        .orElseGet(() -> coerceAtRuntime(adapter.adapt(argument), PrimitiveElement.INT));
            } else {
                argumentCommand = adapter.adapt(argument);
            }

            MethodElement getMethod = context.getClassElement(List.class).getEnclosedElement(
                            ElementQuery.ALL_METHODS
                                    .onlyInstance()
                                    .onlyAccessible()
                                    .named(Predicate.isEqual("get"))
                                    .filter(m -> m.getParameters().length == 1)
                                    .filter(m -> ElementUtils.isAssignable(m.getParameters()[0].getType(), int.class))
                    )
                    .orElseThrow(() -> context.compileError("No get method found in List interface."));

            return methodVisitor -> {
                argumentCommand.render(methodVisitor);
                RenderUtils.renderMethodCall(methodVisitor, getMethod);
                methodVisitor.checkCast(RenderUtils.type(classElement));
            };
        }
    }

    class MapGetterContinuation implements ExpressionContinuation {

        private final ClassElement classElement;

        MapGetterContinuation() {
            this.classElement = Optional.of(previous.getClassElement().getTypeArguments().values())
                    .flatMap(a -> a.stream()
                            .skip(1)
                            .findFirst()
                    )
                    .filter(e -> !e.isGenericPlaceholder())
                    .orElseGet(() -> context.getClassElement(Object.class));
        }

        private ExpressionContext.RenderCommand literalToMapKeyRenderCommand(
                RenderingAdapter adapter,
                Literal literal,
                ClassElement targetType) {
            return literal.accept(new LiteralVisitor<>() {

                @Override
                public ExpressionContext.RenderCommand visit(NullLiteral literal) {
                    // Null is a perfectly valid Map key. But we have to handle it specially as it has Object type.
                    return adapter.adapt(argument);
                }

                @Override
                public ExpressionContext.RenderCommand visit(StringLiteral literal) {
                    // Use String coercion. This may result in optimized enum constant load or compile time String to
                    //  int conversion.
                    return RenderUtils.coerceOrThrow(context, literal.getValue(), targetType);
                }

                @Override
                public ExpressionContext.RenderCommand visit(DecimalLiteral literal) {
                    return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                }

                @Override
                public ExpressionContext.RenderCommand visit(FloatingPointLiteral literal) {
                    return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                }

                @Override
                public ExpressionContext.RenderCommand visit(BooleanLiteral literal) {
                    return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                }
            });
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassElement getClassElement() {
            return classElement;
        }

        @Override
        public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
            ClassElement firstTypeArgument = previous.getClassElement().getFirstTypeArgument()
                    .filter(e -> !e.isGenericPlaceholder())
                    .orElseGet(() -> context.getClassElement(Object.class));

            ExpressionContext.RenderCommand argumentCommand;

            if (!ElementUtils.isAssignable(argument.getClassElement(), firstTypeArgument)) {
                argumentCommand = argument.asLiteral()
                        .map(l -> literalToMapKeyRenderCommand(adapter, l, firstTypeArgument))
                        // Do runtime coercion if it is not a literal.
                        .orElseGet(() -> coerceAtRuntime(adapter.adapt(argument), firstTypeArgument));
            } else {
                argumentCommand = adapter.adapt(argument);
            }

            MethodElement getMethod = context.getClassElement(Map.class).getEnclosedElement(
                            ElementQuery.ALL_METHODS
                                    .onlyInstance()
                                    .onlyAccessible()
                                    .named(Predicate.isEqual("get"))
                                    .filter(m -> m.getParameters().length == 1)
                                    .filter(m ->
                                            ElementUtils.isAssignable(m.getParameters()[0].getType(), Object.class)
                                    )
                    )
                    .orElseThrow(() -> context.compileError("No get method found in List interface."));

            return methodVisitor -> {
                argumentCommand.render(methodVisitor);
                RenderUtils.renderMethodCall(methodVisitor, getMethod);
                methodVisitor.checkCast(RenderUtils.type(classElement));
            };

        }
    }

    class ArrayGetterContinuation implements ExpressionContinuation {

        private final ClassElement classElement;

        ArrayGetterContinuation() {
            classElement = previous.getClassElement().fromArray();
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassElement getClassElement() {
            return classElement;
        }

        @Override
        public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
            ExpressionContext.RenderCommand argumentCommand;

            if (!ElementUtils.isAssignable(argument.getClassElement(), int.class)) {
                argumentCommand = argument.asLiteral()
                        .map(CollectionContinuation.this::literalToIndexRenderCommand)
                        // Do runtime coercion if it is not a literal.
                        .orElseGet(() -> coerceAtRuntime(adapter.adapt(argument), PrimitiveElement.INT));
            } else {
                argumentCommand = adapter.adapt(argument);
            }

            return methodVisitor -> {
                argumentCommand.render(methodVisitor);

                Type objectType = RenderUtils.type(classElement);
                methodVisitor.arrayLoad(objectType);
                if (classElement.isArray()) {
                    methodVisitor.checkCast(objectType);
                }
            };
        }
    }

    class StringCharGetterContinuation implements ExpressionContinuation {

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassElement getClassElement() {
            return PrimitiveElement.CHAR;
        }

        @Override
        public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
            ClassElement previousClasElement = previous.getClassElement();

            ExpressionContext.RenderCommand coerceToStringCommand =
                    !ElementUtils.isAssignable(previousClasElement, String.class)
                            ? RenderUtils.coerceOrThrow(
                                    context, previousClasElement, context.getClassElement(String.class))
                            : null;
            ExpressionContext.RenderCommand argumentCommand;

            if (!ElementUtils.isAssignable(argument.getClassElement(), int.class)) {
                argumentCommand = argument.asLiteral()
                        .map(CollectionContinuation.this::literalToIndexRenderCommand)
                        // Do runtime coercion if it is not a literal.
                        .orElseGet(() -> coerceAtRuntime(adapter.adapt(argument), PrimitiveElement.INT));
            } else {
                argumentCommand = adapter.adapt(argument);
            }

            return methodVisitor -> {
                if (coerceToStringCommand != null) {
                    coerceToStringCommand.render(methodVisitor);
                }
                argumentCommand.render(methodVisitor);

                methodVisitor.invokeVirtual(
                        Type.getType(String.class),
                        new Method("charAt", "(I)C")
                );
            };
        }
    }

    private final ExpressionContext context;
    private final ExpressionContinuation previous;
    private final ExpressionContinuation argument;

    private ExpressionContinuation delegate;

    CollectionContinuation(String name,
            ExpressionContext context,
            ExpressionContinuation previous,
            ExpressionContinuation argument) {
        super(name);

        this.context = context;
        this.previous = previous;
        this.argument = argument;
    }

    @Override
    public boolean canBeRun() {
        return argument.canBeRun();
    }

    private ExpressionContext.RenderCommand literalToIndexRenderCommand(Literal literal) {
        return literal.accept(new LiteralVisitor<>() {

            @Override
            public ExpressionContext.RenderCommand visit(NullLiteral literal) {
                throw context.compileError(INDEX_NULL_TYPE_ERROR);
            }

            @Override
            public ExpressionContext.RenderCommand visit(StringLiteral literal) {
                // Use String coercion. It is a compile error if literal does not contain parsable int
                //  value.
                return RenderUtils.coerceOrThrow(context, literal.getValue(), PrimitiveElement.INT);
            }

            @Override
            public ExpressionContext.RenderCommand visit(DecimalLiteral literal) {
                // Load decimal literal value directly as int to avoid runtime casts. Do additional compile
                //  time checks.
                int index = asIndex(context, literal.getValue());

                return methodVisitor -> methodVisitor.push(index);
            }

            @Override
            public ExpressionContext.RenderCommand visit(FloatingPointLiteral literal) {
                // Load fractional literal value directly as int to avoid runtime casts. Do additional compile
                //  time checks.
                int index = asIndex(context, literal.getValue());

                return methodVisitor -> methodVisitor.push(index);
            }

            @Override
            public ExpressionContext.RenderCommand visit(BooleanLiteral literal) {
                throw context.compileError(INDEX_BOOLEAN_TYPE_ERROR);
            }
        });
    }

    private ExpressionContext.RenderCommand coerceAtRuntime(
            ExpressionContext.RenderCommand argumentCommand, ClassElement targetType) {
        ExpressionContext.RenderCommand coerceCommand = RenderUtils.coerceOrThrow(
                context, argument.getClassElement(), targetType
        );

        return methodVisitor -> {
            argumentCommand.render(methodVisitor);
            coerceCommand.render(methodVisitor);
        };
    }

    @Override
    public int requiresMonitoringCount() {
        ClassElement previousClasElement = previous.getClassElement();

        return ElementUtils.isAssignable(previousClasElement, ObservableList.class)
                || ElementUtils.isAssignable(previousClasElement, ObservableMap.class) ? 1 : 0;
    }

    private ExpressionContinuation getDelegate() {
        if (this.delegate == null) {
            ClassElement previousClasElement = previous.getClassElement();

            if (ElementUtils.isAssignable(previousClasElement, List.class)) {
                this.delegate = new ListGetterContinuation();
            } else if (ElementUtils.isAssignable(previousClasElement, Map.class)) {
                this.delegate = new MapGetterContinuation();
            } else if (previousClasElement.isArray()) {
                this.delegate = new ArrayGetterContinuation();
            } else {
                // Coerce to String and load character by index.
                this.delegate = new StringCharGetterContinuation();
            }
        }

        return this.delegate;
    }

    @Override
    public ClassElement getClassElement() {
        return getDelegate().getClassElement();
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        return getDelegate().run(adapter);
    }
}
