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
import io.micronaut.inject.ast.PropertyElement;
import javafx.beans.value.ObservableValue;

import java.util.Optional;
import java.util.function.Predicate;

class PropertyReadContinuation extends AbstractNamedContinuation {

    static final String NO_SUCH_PROPERTY_ERROR_FORMAT = "Unable to find property \"%1$s\" of class \"%2$s\".";

    private final ExpressionContinuation previous;
    private final ExpressionContext context;

    private PropertyElement property;
    private Optional<MethodElement> propertyModel;

    PropertyReadContinuation(String identifier, ExpressionContinuation previous, ExpressionContext context) {
        super(identifier);

        this.previous = previous;
        this.context = context;
    }

    private PropertyElement getProperty() {
        if (this.property == null) {
            ClassElement previousClassElement = previous.getClassElement();

            this.property = previousClassElement.getBeanProperties().stream()
                    .filter(p -> p.getName().equals(getName()))
                    .findFirst()
                    .map(ElementUtils::fixProperty)
                    .orElseThrow(() -> context.compileError(
                            String.format(
                                    NO_SUCH_PROPERTY_ERROR_FORMAT,
                                    getName(),
                                    ElementUtils.getSimpleName(previousClassElement)
                            )
                    ));
        }

        return this.property;
    }

    Optional<MethodElement> getPropertyModel() {
        if (this.propertyModel == null) {
            PropertyElement propertyElement = getProperty();

            ClassElement previousClassElement = previous.getClassElement();

            this.propertyModel = previousClassElement.getEnclosedElement(
                    ElementQuery.ALL_METHODS
                            .onlyInstance()
                            .onlyAccessible()
                            .named(Predicate.isEqual(propertyElement.getName() + "Property"))
                            .filter(e -> e.getParameters().length == 0)
                            .filter(e -> ElementUtils.isAssignable(e.getReturnType(), ObservableValue.class))
            );
        }

        return propertyModel;
    }

    @Override
    public int requiresMonitoringCount() {
        return getPropertyModel().isPresent() ? 1 : 0;
    }

    @Override
    public ClassElement getClassElement() {
        return getProperty().getType();
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        PropertyElement property = getProperty();

        MethodElement getter = property.getReadMethod()
                // NB: Property must always have read method, or it will not be considered as property by Micronaut.
                .orElseThrow(AssertionError::new);

        return methodVisitor -> RenderUtils.renderMethodCall(methodVisitor, getter);
    }

    @Override
    public <T> T accept(ExpressionContinuationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
