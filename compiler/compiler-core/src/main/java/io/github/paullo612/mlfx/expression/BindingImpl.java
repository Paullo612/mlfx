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
import io.micronaut.inject.ast.PrimitiveElement;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.FloatPropertyBase;
import javafx.beans.property.IntegerPropertyBase;
import javafx.beans.property.LongPropertyBase;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.Property;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.util.List;
import java.util.Map;

class BindingImpl extends StaticBindingImpl<BindingContext> {

    private static final Map<List<Class<?>>, PropertyHandler> PROPERTY_HANDLERS = Map.of(
            List.of(boolean.class, Boolean.class),
            new PrimitivePropertyHandler(BooleanPropertyBase.class, PrimitiveElement.BOOLEAN),
            List.of(double.class, Double.class),
            new PrimitivePropertyHandler(DoublePropertyBase.class, PrimitiveElement.DOUBLE),
            List.of(long.class, Long.class),
            new PrimitivePropertyHandler(LongPropertyBase.class, PrimitiveElement.LONG),
            List.of(float.class, Float.class),
            new PrimitivePropertyHandler(FloatPropertyBase.class, PrimitiveElement.FLOAT),
            List.of(int.class, Integer.class),
            new PrimitivePropertyHandler(IntegerPropertyBase.class, PrimitiveElement.INT)

            // NB: There is no special handling for String, Map, Set or List properties, as those do not check for
            //  property type in theirs bind method (there is no instanceof based optimizations).
    );

    private interface PropertyHandler {

        BindingContext.BindingExpressionRenderer createExpressionRenderer(BindingContext context, String expression);

        void finishRendering(
                BindingContext context,
                BindingContext.BindingExpressionRenderer expressionRenderer,
                ClassElement classElement
        );
    }

    private static abstract class AbstractPropertyHandler implements PropertyHandler {

        void renderSetter(
                BindingContext.BindingExpressionRenderer expressionRenderer,
                Class<? extends Property> propertyClass,
                ClassElement targetType) {
            expressionRenderer.render(methodVisitor -> {
                methodVisitor.loadThis();
                methodVisitor.swap();
                methodVisitor.invokeVirtual(
                        Type.getType(propertyClass),
                        new Method("set", "(" + RenderUtils.type(targetType) + ")V")
                );
            });
        }
    }

    private static class PrimitivePropertyHandler extends AbstractPropertyHandler {

        private final Class<? extends Property> propertyClass;
        private final PrimitiveElement targetType;

        private PrimitivePropertyHandler(
                Class<? extends Property> propertyClass,
                PrimitiveElement classElement) {
            this.propertyClass = propertyClass;
            this.targetType = classElement;
        }

        @Override
        public BindingContext.BindingExpressionRenderer createExpressionRenderer(
                BindingContext context,
                String expression) {
            return context.createExpressionRenderer(expression, propertyClass, null);
        }

        @Override
        public void finishRendering(
                BindingContext context,
                BindingContext.BindingExpressionRenderer expressionRenderer,
                ClassElement classElement) {
            if (!ElementUtils.isAssignable(classElement, targetType)) {
                expressionRenderer.render(
                        RenderUtils.coerceOrThrow(context, classElement, targetType)
                );
            }

            renderSetter(expressionRenderer, propertyClass, targetType);
        }
    }

    private static class ObjectPropertyHandler extends AbstractPropertyHandler {

        private final ClassElement targetType;

        ObjectPropertyHandler(ClassElement targetType) {
            this.targetType = targetType;
        }

        @Override
        public BindingContext.BindingExpressionRenderer createExpressionRenderer(
                BindingContext context,
                String expression) {
            return context.createExpressionRenderer(expression, ObjectPropertyBase.class, targetType);
        }

        @Override
        public void finishRendering(
                BindingContext context,
                BindingContext.BindingExpressionRenderer expressionRenderer,
                ClassElement classElement) {
            if (!ElementUtils.isAssignable(classElement, targetType)) {
                expressionRenderer.render(
                        RenderUtils.coerceOrThrow(context, classElement, targetType)
                );
            }

            // NB: We can use |ClassElement.of| here, as there will be no |isAssignable| calls.
            renderSetter(expressionRenderer, ObjectPropertyBase.class, ClassElement.of(Object.class));
        }
    }

    private final String expression;
    private Expressions.BindingType bindingType;

    BindingImpl(
            BindingContext context,
            ExpressionContinuation continuation,
            ClassElement targetType,
            String expression) {
        super(context, continuation, targetType);
        this.expression = expression;
    }

    @Override
    public Expressions.BindingType getBindingType() {
        if (this.bindingType == null) {
            this.bindingType = getContinuation().requiresMonitoringCount() > 0
                    ? Expressions.BindingType.DYNAMIC
                    : Expressions.BindingType.STATIC;
        }

        return bindingType;
    }

    @Override
    public ExpressionContext.RenderCommand run() {
        Expressions.BindingType bindingType = getBindingType();

        if (bindingType == Expressions.BindingType.STATIC) {
            return super.run();
        }

        BindingContext context = getContext();
        ExpressionContinuation continuation = getContinuation();
        ClassElement targetType = getTargetType()
                .orElse(continuation.getClassElement());

        PropertyHandler propertyHandler = null;

        for (Map.Entry<List<Class<?>>, PropertyHandler> entry : PROPERTY_HANDLERS.entrySet()) {
            for (Class<?> handlerClass : entry.getKey()) {
                if (ElementUtils.isAssignable(targetType, handlerClass)) {
                    propertyHandler = entry.getValue();
                    break;
                }
            }
        }

        if (propertyHandler == null) {
            propertyHandler = new ObjectPropertyHandler(targetType);
        }

        BindingContext.BindingExpressionRenderer expressionRenderer =
                propertyHandler.createExpressionRenderer(context, expression);

        SavePointRenderingAdapter adapter = new SavePointRenderingAdapter(expressionRenderer);
        expressionRenderer.render(adapter.adapt(continuation));

        propertyHandler.finishRendering(context, expressionRenderer, continuation.getClassElement());

        return expressionRenderer.newInstance();
    }
}
