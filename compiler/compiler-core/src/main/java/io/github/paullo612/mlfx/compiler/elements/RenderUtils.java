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

import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class RenderUtils {

    public static final String COERCE_ERROR_FORMAT = "Unable to coerce %1$s to %2$s.";

    public static final String CONSTRUCTOR_N = "<init>";
    public static final String OBJECT_N = "java/lang/Object";

    public static final String OBJECT_D = "L" + OBJECT_N + ";";
    public static final String STRING_D = "Ljava/lang/String;";

    public static final String TO_STRING_M = "toString";
    public static final String VALUE_OF_M = "valueOf";

    public static final Map<Class<?>, PrimitiveElement> BOXED_PRIMITIVES = Map.of(
            Boolean.class, PrimitiveElement.BOOLEAN,
            Character.class, PrimitiveElement.CHAR,
            Byte.class, PrimitiveElement.BYTE,
            Short.class, PrimitiveElement.SHORT,
            Integer.class, PrimitiveElement.INT,
            Long.class, PrimitiveElement.LONG,
            Float.class, PrimitiveElement.FLOAT,
            Double.class, PrimitiveElement.DOUBLE
    );

    public static Type type(ClassElement element) {
        if (element.isPrimitive()) {
            Type type;

            if (element.isAssignable(int.class)) {
                type = Type.INT_TYPE;
            } else if (element.isAssignable(void.class)) {
                type = Type.VOID_TYPE;
            } else if (element.isAssignable(boolean.class)) {
                type = Type.BOOLEAN_TYPE;
            } else if (element.isAssignable(byte.class)) {
                type = Type.BYTE_TYPE;
            } else if (element.isAssignable(char.class)) {
                type = Type.CHAR_TYPE;
            } else if (element.isAssignable(short.class)) {
                type = Type.SHORT_TYPE;
            } else if (element.isAssignable(double.class)) {
                type = Type.DOUBLE_TYPE;
            } else if (element.isAssignable(float.class)) {
                type = Type.FLOAT_TYPE;
            } else if (element.isAssignable(long.class)) {
                type = Type.LONG_TYPE;
            } else {
                throw new AssertionError();
            }

            if (element.isArray()) {
                return Type.getType("[".repeat(element.getArrayDimensions()) + type);
            }

            return type;
        }

        String objectType = element.getName().replace('.', '/');

        if (element.isArray()) {
            objectType = "[".repeat(element.getArrayDimensions()) + "L" + objectType + ";";
        }

        return Type.getObjectType(objectType);
    }

    public static void renderMethodCall(GeneratorAdapter methodVisitor, MethodElement methodElement) {
        String descriptor = Arrays.stream(methodElement.getParameters())
                .map(ParameterElement::getType)
                .map(RenderUtils::type)
                .map(Type::getDescriptor)
                .collect(Collectors.joining("", "(", ")" + type(methodElement.getReturnType()).getDescriptor()));

        ClassElement owningClassElement = methodElement.getOwningType();
        Type declaringType = type(owningClassElement);
        Method method = new Method(methodElement.getName(), descriptor);

        if (owningClassElement.isInterface()) {
            methodVisitor.invokeInterface(declaringType, method);
        } else {
            methodVisitor.invokeVirtual(declaringType, method);
        }
    }

    private static String camelCaseToEnumConstant(String value) {
        StringBuilder enumConstant = new StringBuilder();

        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);

            if (Character.isUpperCase(c)) {
                enumConstant.append('_');
            }

            enumConstant.append(Character.toUpperCase(c));
        }

        return enumConstant.toString();
    }

    public static ExpressionContext.RenderCommand coerceOrThrow(
            ExpressionContext context,
            String value,
            ClassElement targetType) {
        return coerce(value, targetType)
                .orElseThrow(() -> context.compileError(
                        String.format(COERCE_ERROR_FORMAT, value, ElementUtils.getSimpleName(targetType))
                ));
    }

    private static Number parseNumber(String value) {
        if (value.contains(".")) {
            return Double.parseDouble(value);
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // Infinity case.
            return Double.parseDouble(value);
        }
    }

    public static Optional<ExpressionContext.RenderCommand> coerce(String value, ClassElement targetType) {
        if (targetType.isArray()) {
            // String can never be coerced to array.
            return Optional.empty();
        }

        try {
            if (ElementUtils.isAssignable(targetType, CharSequence.class)) {
                return Optional.of(methodVisitor -> methodVisitor.push(value));
            } else if (ElementUtils.isAssignable(targetType, Boolean.class)) {
                boolean result = Boolean.parseBoolean(value);

                return Optional.of(methodVisitor -> {
                    Type type = Type.getType(Boolean.class);
                    methodVisitor.getStatic(type, result ? "TRUE" : "FALSE", type);
                });
            } else if (ElementUtils.isAssignable(targetType, boolean.class)) {
                boolean result = Boolean.parseBoolean(value);

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Character.class)) {
                char result = value.charAt(0);

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(char.class));
                });
            } else if (ElementUtils.isAssignable(targetType, char.class)) {
                char result = value.charAt(0);

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Byte.class)) {
                byte result = parseNumber(value).byteValue();

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(byte.class));
                });
            } else if (ElementUtils.isAssignable(targetType, byte.class)) {
                byte result = parseNumber(value).byteValue();

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Short.class)) {
                short result = parseNumber(value).shortValue();

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(short.class));
                });
            } else if (ElementUtils.isAssignable(targetType, short.class)) {
                short result = parseNumber(value).shortValue();

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Integer.class)) {
                int result = parseNumber(value).intValue();

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(int.class));
                });
            } else if (ElementUtils.isAssignable(targetType, int.class)) {
                int result = parseNumber(value).intValue();

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Long.class)) {
                long result = parseNumber(value).longValue();

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(long.class));
                });
            } else if (ElementUtils.isAssignable(targetType, long.class)) {
                long result = parseNumber(value).longValue();

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, BigInteger.class)) {
                new BigInteger(value);

                return Optional.of(methodVisitor -> {
                    Type type = Type.getType(BigInteger.class);
                    methodVisitor.newInstance(type);
                    methodVisitor.dup();
                    methodVisitor.invokeConstructor(type, new Method(CONSTRUCTOR_N, "(" + STRING_D + ")V"));
                });
            } else if (ElementUtils.isAssignable(targetType, Float.class)) {
                float result = parseNumber(value).floatValue();

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(float.class));
                });
            } else if (ElementUtils.isAssignable(targetType, float.class)) {
                float result = parseNumber(value).floatValue();

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Double.class)) {
                double result = parseNumber(value).doubleValue();

                return Optional.of(methodVisitor -> {
                    methodVisitor.push(result);
                    methodVisitor.valueOf(Type.getType(double.class));
                });
            } else if (ElementUtils.isAssignable(targetType, double.class)) {
                double result = parseNumber(value).doubleValue();

                return Optional.of(methodVisitor -> methodVisitor.push(result));
            } else if (ElementUtils.isAssignable(targetType, Number.class)) {
                // NB: Looks quite incorrect, as Infinity is not a Long, but does not contain '.'. But FXMLLoader does
                //  just that, so, stay complaint.
                if (value.contains(".")) {
                    double result = Double.parseDouble(value);

                    return Optional.of(methodVisitor -> {
                        methodVisitor.push(result);
                        methodVisitor.valueOf(Type.getType(double.class));
                    });
                } else {
                    long result = Long.parseLong(value);

                    return Optional.of(methodVisitor -> {
                        methodVisitor.push(result);
                        methodVisitor.valueOf(Type.getType(long.class));
                    });
                }
            } else if (ElementUtils.isAssignable(targetType, BigDecimal.class)) {
                new BigDecimal(value);

                return Optional.of(methodVisitor -> {
                    Type type = Type.getType(BigDecimal.class);
                    methodVisitor.newInstance(type);
                    methodVisitor.dup();
                    methodVisitor.invokeConstructor(type, new Method(CONSTRUCTOR_N, "(" + STRING_D + ")V"));
                });
            } else if (ElementUtils.isAssignable(targetType, Class.class)) {
                return Optional.of(methodVisitor ->
                        methodVisitor.push(Type.getObjectType(value.replace('.', '/')))
                );
            } else {
                if (targetType.isEnum()) {
                    // Load enum constant directly if target type is an enum.
                    String enumConstantValue = Character.isLowerCase(value.charAt(0))
                            ? camelCaseToEnumConstant(value)
                            : value;

                    return targetType.getEnclosedElement(
                                    ((ElementQuery<? extends TypedElement>) ElementQuery.ALL_FIELDS)
                                            .modifiers(m -> m.contains(ElementModifier.STATIC))
                                            .onlyAccessible()
                                            .includeEnumConstants()
                                            .named(Predicate.isEqual(enumConstantValue))
                                            .filter(m -> ElementUtils.isAssignable(m.getType(), targetType))
                            )
                            .map(f -> methodVisitor -> {
                                Type type = type(targetType);

                                methodVisitor.getStatic(
                                        type,
                                        enumConstantValue,
                                        type
                                );
                            });
                }

                // Try to find and use static |valueOf(String value)| method.
                return targetType.getEnclosedElement(
                                ElementQuery.ALL_METHODS
                                        .modifiers(m -> m.contains(ElementModifier.STATIC))
                                        .onlyAccessible()
                                        .named(Predicate.isEqual(VALUE_OF_M))
                                        .filter(m -> m.getParameters().length == 1)
                                        .filter(m ->
                                                ElementUtils.isAssignable(m.getParameters()[0].getType(), String.class)
                                        )
                        )
                        .map(m -> methodVisitor -> {
                            Type type = type(targetType);

                            methodVisitor.push(value);
                            methodVisitor.invokeStatic(
                                    type, new Method(m.getName(), "(" + STRING_D + ")" + type.getDescriptor())
                            );
                        });
            }
        } catch (NumberFormatException e) {
            // ignore
        }

        return Optional.empty();
    }

    private static Optional<ExpressionContext.RenderCommand> coerceToPrimitive(
            ClassElement sourceType,
            Type primitive,
            Type boxed) {
        if (sourceType.isArray()) {
            return Optional.empty();
        }

        // Booleans should not be cast.
        if (ElementUtils.isAssignable(sourceType, boolean.class)
                || ElementUtils.isAssignable(sourceType, Boolean.class)) {
            return Optional.empty();
        }

        // Downcast, if required.
        if (ElementUtils.isPrimitive(sourceType)) {
            return Optional.of(methodVisitor -> methodVisitor.cast(type(sourceType), primitive));
        }

        // Same as unbox, but covers any Number as source type.
        //
        // source.typeValue();
        if (ElementUtils.isAssignable(sourceType, Number.class)) {
            return Optional.of(methodVisitor -> methodVisitor.invokeVirtual(
                    Type.getType(Number.class),
                    new Method(primitive.getClassName() + "Value", "()" + primitive.getDescriptor())
            ));
        }

        // Boxed.parseType(source.toString());
        return Optional.of(methodVisitor -> {
            methodVisitor.invokeVirtual(
                    Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
            );
            methodVisitor.invokeStatic(
                    boxed,
                    new Method(
                            "parse" + NameUtils.capitalize(primitive.getClassName()),
                            "(" + STRING_D + ")" + primitive.getDescriptor()
                    )
            );
        });
    }

    private static Optional<ExpressionContext.RenderCommand> coerceToBoxedPrimitive(
            ClassElement sourceType,
            Type primitive,
            Type boxed) {
        if (sourceType.isArray()) {
            return Optional.empty();
        }

        // Booleans should not be boxed.
        if (ElementUtils.isAssignable(sourceType, boolean.class)
                || ElementUtils.isAssignable(sourceType, Boolean.class)) {
            return Optional.empty();
        }

        // Downcast, if required.
        //
        // Boxed.valueOf((primitive) source);
        if (ElementUtils.isPrimitive(sourceType)) {
            return Optional.of(methodVisitor -> {
                methodVisitor.cast(type(sourceType), primitive);
                methodVisitor.valueOf(primitive);
            });
        }

        // Cast through Number class with potential downcast.
        //
        // Boxed.valueOf(source.primitiveValue());
        if (ElementUtils.isAssignable(sourceType, Number.class)) {
            return Optional.of(methodVisitor -> {
                methodVisitor.invokeVirtual(
                        Type.getType(Number.class),
                        new Method(primitive.getClassName() + "Value", "()" + primitive.getDescriptor())
                );
                methodVisitor.valueOf(primitive);
            });
        }

        // Boxed.valueOf(source.toString());
        return Optional.of(methodVisitor -> {
            methodVisitor.invokeVirtual(
                    Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
            );
            methodVisitor.invokeStatic(
                    boxed, new Method(VALUE_OF_M, "(" + STRING_D + ")" + boxed.getDescriptor())
            );
        });
    }

    public static ExpressionContext.RenderCommand coerceOrThrow(
            ExpressionContext context,
            ClassElement sourceType,
            ClassElement targetType) {
        return coerce(sourceType, targetType)
                .orElseThrow(() -> context.compileError(
                        String.format(
                                COERCE_ERROR_FORMAT, ElementUtils.getSimpleName(sourceType), ElementUtils.getSimpleName(targetType)
                        )
                ));
    }

    public static Optional<ExpressionContext.RenderCommand> coerce(ClassElement sourceType, ClassElement targetType) {
        return doCoerce(sourceType, targetType)
                .map(c -> {
                   // Render null check if required.
                   if (ElementUtils.isPrimitive(sourceType)) {
                       // Primitive cannot be null.
                       return c;
                   }

                   if (ElementUtils.isPrimitive(targetType)) {
                       // Boxing case. Let NullPointerException be thrown at runtime.
                       return c;
                   }

                   // source != null ? ... : null;
                   return methodVisitor -> {
                       Label out = methodVisitor.newLabel();
                       Label nullLabel = methodVisitor.newLabel();
                       methodVisitor.dup();
                       methodVisitor.ifNull(nullLabel);
                       c.render(methodVisitor);
                       methodVisitor.goTo(out);
                       methodVisitor.mark(nullLabel);
                       methodVisitor.pop();
                       methodVisitor.push((Type) null);
                       methodVisitor.mark(out);
                   };
                });
    }

    private static Optional<ExpressionContext.RenderCommand> doCoerce(
            ClassElement sourceType,
            ClassElement targetType) {
        assert !ElementUtils.isAssignable(sourceType, targetType);

        if (ElementUtils.isAssignable(targetType, boolean.class)) {
            if (sourceType.isArray()) {
                return Optional.empty();
            }

            // source.booleanValue();
            if (ElementUtils.isAssignable(sourceType, Boolean.class)) {
                return Optional.of(methodVisitor ->
                        methodVisitor.unbox(Type.getType(boolean.class))
                );
            }

            // NB: primitive to boolean coercion never evaluates to true
            //  |Boolean.parseBoolean(String.valueOf(source))|
            // false
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.pop();
                    methodVisitor.push(false);
                });
            }

            // Boolean.parseBoolean(source.toString());
            return Optional.of(methodVisitor -> {
                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.invokeStatic(
                        Type.getType(Boolean.class), new Method("parseBoolean", "(" + STRING_D + ")Z")
                );
            });
        } else if (ElementUtils.isAssignable(targetType, Boolean.class)) {
            if (sourceType.isArray()) {
                return Optional.empty();
            }

            // Only applicable to primitive boolean.
            //
            // Boolean.valueOf(source);
            if (ElementUtils.isAssignable(sourceType, boolean.class)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.cast(type(sourceType), Type.getType(boolean.class));
                    methodVisitor.valueOf(Type.getType(boolean.class));
                });
            }

            // NB: primitive to boolean coercion never evaluates to true
            //  |Boolean.valueOf(String.valueOf(source))|
            // Boolean.FALSE
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.pop();

                    Type objectType = Type.getType(Boolean.class);
                    methodVisitor.getStatic(objectType, "FALSE", objectType);
                });
            }

            // Boolean.valueOf(source.toString());
            return Optional.of(methodVisitor -> {
                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.invokeStatic(
                        Type.getType(Boolean.class), new Method(VALUE_OF_M, "(" + STRING_D + ")Ljava/lang/Boolean;")
                );
            });
        } else if (ElementUtils.isAssignable(targetType, char.class)) {
            if (sourceType.isArray()) {
                return Optional.empty();
            }

            // Downcast, if required.
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor ->
                        methodVisitor.cast(type(sourceType), Type.getType(char.class))
                );
            }

            // source.charValue();
            if (ElementUtils.isAssignable(sourceType, Character.class)) {
                return Optional.of(methodVisitor ->
                        methodVisitor.unbox(Type.getType(char.class))
                );
            }

            // source.toString().charAt(0);
            return Optional.of(methodVisitor -> {
                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.invokeStatic(
                        Type.getType(String.class), new Method("charAt", "(I)C")
                );
            });
        } else if (ElementUtils.isAssignable(targetType, Character.class)) {
            if (sourceType.isArray()) {
                return Optional.empty();
            }

            // Downcast, if required.
            //
            // Character.valueOf((char) source);
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.cast(type(sourceType), Type.getType(char.class));
                    methodVisitor.valueOf(Type.getType(char.class));
                });
            }

            // Character.valueOf(source.toString().charAt(0));
            return Optional.of(methodVisitor -> {
                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.invokeStatic(
                        Type.getType(String.class), new Method("charAt", "(I)C")
                );
                methodVisitor.valueOf(Type.getType(char.class));
            });
        } else if (ElementUtils.isAssignable(targetType, byte.class)) {
            return coerceToPrimitive(sourceType, Type.getType(byte.class), Type.getType(Byte.class));
        } else if (ElementUtils.isAssignable(targetType, Byte.class)) {
            return coerceToBoxedPrimitive(sourceType, Type.getType(byte.class), Type.getType(Byte.class));
        } else if (ElementUtils.isAssignable(targetType, short.class)) {
            return coerceToPrimitive(sourceType, Type.getType(short.class), Type.getType(Short.class));
        } else if (ElementUtils.isAssignable(targetType, Short.class)) {
            return coerceToBoxedPrimitive(sourceType, Type.getType(short.class), Type.getType(Short.class));
        } else if (ElementUtils.isAssignable(targetType, int.class)) {
            return coerceToPrimitive(sourceType, Type.getType(int.class), Type.getType(Integer.class));
        } else if (ElementUtils.isAssignable(targetType, Integer.class)) {
            return coerceToBoxedPrimitive(sourceType, Type.getType(int.class), Type.getType(Integer.class));
        } else if (ElementUtils.isAssignable(targetType, long.class)) {
            return coerceToPrimitive(sourceType, Type.getType(long.class), Type.getType(Long.class));
        } else if (ElementUtils.isAssignable(targetType, Long.class)) {
            return coerceToBoxedPrimitive(sourceType, Type.getType(long.class), Type.getType(Long.class));
        } else if (ElementUtils.isAssignable(targetType, BigInteger.class)) {
            // Downcast, if required.
            //
            // BigInteger.valueOf((long) source);
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.cast(type(sourceType), Type.getType(long.class));
                    methodVisitor.invokeStatic(
                            Type.getType(BigInteger.class), new Method(VALUE_OF_M, "(J)Ljava/math/BigInteger;")
                    );
                });
            }

            // Cast through Number class with potential downcast.
            //
            // BigInteger.valueOf(source.longValue());
            if (ElementUtils.isAssignable(sourceType, Number.class)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.invokeVirtual(
                            Type.getType(Number.class), new Method("longValue", "()J")
                    );
                    methodVisitor.invokeStatic(
                            Type.getType(BigInteger.class), new Method(VALUE_OF_M, "(J)Ljava/math/BigInteger;")
                    );
                });
            }

            // new BigInteger(source.toString());
            return Optional.of(methodVisitor -> {
                Type bigIntegerObject = Type.getType(BigInteger.class);

                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.newInstance(bigIntegerObject);
                methodVisitor.dupX1();
                methodVisitor.swap();
                methodVisitor.invokeConstructor(
                        bigIntegerObject, new Method(CONSTRUCTOR_N, "(" + STRING_D + ")V")
                );
            });
        } else if (ElementUtils.isAssignable(targetType, float.class)) {
            return coerceToPrimitive(sourceType, Type.getType(float.class), Type.getType(Float.class));
        } else if (ElementUtils.isAssignable(targetType, Float.class)) {
            return coerceToBoxedPrimitive(sourceType, Type.getType(float.class), Type.getType(Float.class));
        } else if (ElementUtils.isAssignable(targetType, double.class)) {
            return coerceToPrimitive(sourceType, Type.getType(double.class), Type.getType(Double.class));
        } else if (ElementUtils.isAssignable(targetType, Double.class)) {
            return coerceToBoxedPrimitive(sourceType, Type.getType(double.class), Type.getType(Double.class));
        } else if (ElementUtils.isAssignable(targetType, Number.class)) {
            if (sourceType.isArray()) {
                return Optional.empty();
            }

            // NB: Looks quite incorrect, as Infinity is not a Long, but does not contain '.'. But FXMLLoader does
            //  just that, so, stay complaint.

            // String stringValue = source.toString();
            // stringValue.contains(".") ? Double.valueOf(source) : Long.valueOf(source);
            ExpressionContext.RenderCommand command = methodVisitor -> {
                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.dup();
                methodVisitor.push(".");
                methodVisitor.invokeVirtual(
                        Type.getType(String.class), new Method("contains", "(" + STRING_D + ")Z")
                );

                Label zero = methodVisitor.newLabel();
                Label out = methodVisitor.newLabel();

                methodVisitor.ifZCmp(GeneratorAdapter.EQ, zero);
                methodVisitor.invokeStatic(
                        Type.getType(Double.class),
                        new Method(VALUE_OF_M, "(" + STRING_D + ")Ljava/lang/Double;")
                );
                methodVisitor.goTo(out);
                methodVisitor.mark(zero);
                methodVisitor.invokeStatic(
                        Type.getType(Long.class),
                        new Method(VALUE_OF_M, "(" + STRING_D + ")Ljava/lang/Long;")
                );
                methodVisitor.mark(out);
            };

            // Downcast, if required.
            //
            // String stringValue = Boxed.valueOf(source).toString();
            // stringValue.contains(".") ? Double.valueOf(stringValue) : Long.valueOf(stringValue);
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.valueOf(type(sourceType));

                    command.render(methodVisitor);
                });
            }

            return Optional.of(command);
        } else if (ElementUtils.isAssignable(targetType, BigDecimal.class)) {
            if (sourceType.isArray()) {
                return Optional.empty();
            }

            // Downcast, if required.
            //
            // BigDecimal.valueOf((double) source);
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.cast(type(sourceType), Type.getType(double.class));
                    methodVisitor.invokeStatic(
                            Type.getType(BigDecimal.class), new Method(VALUE_OF_M, "(D)Ljava/math/BigDecimal;")
                    );
                });
            }

            // Cast through Number class with potential downcast.
            //
            // BigDecimal.valueOf(source.doubleValue());
            if (ElementUtils.isAssignable(sourceType, Number.class)) {
                return Optional.of(methodVisitor -> {
                    methodVisitor.invokeVirtual(
                            Type.getType(Number.class), new Method("doubleValue", "()D")
                    );
                    methodVisitor.invokeStatic(
                            Type.getType(BigDecimal.class), new Method(VALUE_OF_M, "(D)Ljava/math/BigDecimal;")
                    );
                });
            }

            // new BigDecimal(source.toString());
            return Optional.of(methodVisitor -> {
                Type bigIntegerObject = Type.getType(BigInteger.class);

                methodVisitor.invokeVirtual(
                        Type.getType(Object.class), new Method(TO_STRING_M, "()" + STRING_D)
                );
                methodVisitor.newInstance(bigIntegerObject);
                methodVisitor.dupX1();
                methodVisitor.swap();
                methodVisitor.invokeConstructor(
                        bigIntegerObject, new Method(CONSTRUCTOR_N, "(" + STRING_D + ")V")
                );
            });
        } else if (ElementUtils.isAssignable(targetType, Class.class)) {
            // NB: We could do Class.forName(source.toString()), but that would require reflections. Render
            //  source.getClass() for objects and put class directly for primitives.
            if (ElementUtils.isPrimitive(sourceType)) {
                return Optional.of(methodVisitor -> {
                    Type primitiveType = type(sourceType);
                    if (primitiveType.getSize() == 1) {
                        methodVisitor.pop();
                    } else {
                        methodVisitor.pop2();
                    }

                    methodVisitor.push(primitiveType);
                });
            }

            return Optional.of(methodVisitor -> methodVisitor.invokeVirtual(
                    Type.getType(Object.class), new Method("getClass", "()" + Type.getType(Class.class).getDescriptor())
            ));
        }

        // NB: What about coercing boxed types to String? It could be nice to implement, maybe, but it is not really
        //  required. The only case is instance declaration with fx:factory or dx:constant that returns primitives (note
        //  that it cannot be copied using fx:copy as there is no appropriate constructor). fx:include uses loader
        //  delegation, thus, primitives are converted to boxed types to make them returnable from loader's load method,
        //  which returns objects, not primitives.

        // Try to find and use static |valueOf(SourceType source)| method.
        return targetType.getEnclosedElement(
                        ElementQuery.ALL_METHODS
                                .modifiers(m -> m.contains(ElementModifier.STATIC))
                                .onlyAccessible()
                                .named(Predicate.isEqual(VALUE_OF_M))
                                .filter(m -> m.getParameters().length == 1)
                                .filter(m -> ElementUtils.isAssignable(sourceType, m.getParameters()[0].getType()))
                )
                .map(m -> methodVisitor -> {
                    Type type = type(targetType);

                    methodVisitor.invokeStatic(
                            type,
                            new Method(
                                    m.getName(),
                                    "(" + type(m.getParameters()[0].getType()) + ")" + type.getDescriptor()
                            )
                    );
                });
    }

    private RenderUtils() {
        super();
    }
}
