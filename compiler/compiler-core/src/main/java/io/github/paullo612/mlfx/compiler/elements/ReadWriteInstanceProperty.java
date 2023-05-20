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
import io.micronaut.inject.ast.PropertyElement;

public class ReadWriteInstanceProperty extends FXMLElement<LoadableFXMLElement<?>> {

    private final PropertyElement propertyElement;

    public ReadWriteInstanceProperty(LoadableFXMLElement<?> parent, PropertyElement propertyElement) {
        super(parent);

        this.propertyElement = propertyElement;
    }

    PropertyElement getPropertyElement() {
        return propertyElement;
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        // No attributes here, sorry.
        throw context.compileError("Attributes are supported only for read only instance property elements.");
    }

    @Override
    public void handleCharacters(CompilerContext context, String text) {
        String value = MULTI_WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();

        getParent().apply(context, this, LoadableFXMLElement.loadString(context, value));
    }

    void apply(CompilerContext context, IdentifiableFXMLElement element) {
        getParent().apply(context, this, LoadableFXMLElement.loadElement(context, element));
    }
}
