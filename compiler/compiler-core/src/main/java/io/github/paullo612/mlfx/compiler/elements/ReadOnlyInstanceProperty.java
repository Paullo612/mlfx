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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

// You can apply attributes, event handlers, or another instance properties to read only instance property, didn't you
//  know? By the way, read only instance property can't be referenced by fx:id. Why?
public class ReadOnlyInstanceProperty extends LoadableFXMLElement<LoadableFXMLElement<?>> {

    private final PropertyElement propertyElement;

    public ReadOnlyInstanceProperty(LoadableFXMLElement<?> parent, PropertyElement propertyElement) {
        super(parent);

        this.propertyElement = propertyElement;
    }

    PropertyElement getPropertyElement() {
        return propertyElement;
    }

    @Override
    public void handleCharacters(CompilerContext context, String text) {
        String value = MULTI_WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();

        getParent().apply(context, this, LoadableFXMLElement.loadString(context, value));
    }

    private MethodElement getGetter() {
        assert propertyElement.getReadMethod().isPresent();
        return propertyElement.getReadMethod().get();
    }

    @Override
    public ClassElement getClassElement() {
        return getGetter().getReturnType();
    }

    void apply(CompilerContext context, IdentifiableFXMLElement element) {
        getParent().apply(context, this, loadElement(context, element));
    }

    @Override
    public ExpressionContext.RenderCommand load() {
        ExpressionContext.RenderCommand loadParent = getParent().load();
        MethodElement getter = getGetter();

        return methodVisitor -> {
            loadParent.render(methodVisitor);
            RenderUtils.renderMethodCall(methodVisitor, getter);
        };
    }
}
