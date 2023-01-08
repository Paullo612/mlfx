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
import io.micronaut.inject.ast.ClassElement;

// NB: There is no such thing as read only static property in FXML. Static property must have both read and write
//  methods defined.
public class StaticPropertyFXMLElement extends FXMLElement<LoadableFXMLElement<?>> {

    private final String name;
    private final ClassElement staticClassElement;
    private String value;

    public StaticPropertyFXMLElement(LoadableFXMLElement<?> parent, String name, ClassElement classElement) {
        super(parent);

        this.name = name;
        this.staticClassElement = classElement;
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        throw context.compileError("Attributes are not supported for static property elements.");
    }

    @Override
    public void handleCharacters(CompilerContext context, String text) {
        value = MULTI_WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }

    @Override
    public void handleEndElement(CompilerContext context) {
        getParent().apply(
                context,
                staticClassElement,
                name,
                LoadableFXMLElement.loadString(context, value)
        );
    }
}
