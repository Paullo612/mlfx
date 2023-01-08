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

import java.util.regex.Pattern;

public abstract class FXMLElement<P extends FXMLElement<?>> {

    static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final P parent;

    FXMLElement(P parent) {
        this.parent = parent;
    }

    public void initialize(CompilerContext context) {
        // Does nothing.
    }

    public boolean requiresAttributesLookahead() {
        return false;
    }

    public void handleAttributeLookahead(CompilerContext context, String prefix, String localName, String value) {
        throw context.compileError("Element does not support attributes.");
    }

    public void handleAttributesLookaheadFinish(CompilerContext context) {
        // Does nothing.
    }

    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        throw context.compileError("Element does not support attributes.");
    }

    public void handleAttributesFinish(CompilerContext context) {
        // Does nothing.
    }

    public void handleCharacters(CompilerContext context, String text) {
        throw context.compileError("Unexpected characters in input stream.");
    }

    void apply(CompilerContext context, IdentifiableFXMLElement element) {
        throw context.compileError("Default instance property is not applicable to this element.");
    }

    public void handleEndElement(CompilerContext context) {
        // Does nothing.
    }

    public P getParent() {
        return parent;
    }

    public LoadableFXMLElement<?> asLoadableFXMLElement() {
        return null;
    }
}
