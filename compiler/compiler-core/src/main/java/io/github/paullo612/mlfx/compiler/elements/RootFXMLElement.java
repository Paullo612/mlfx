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
import io.github.paullo612.mlfx.compiler.ProcessingInstructions;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.inject.ast.ClassElement;
import javafx.fxml.FXMLLoader;

public class RootFXMLElement extends IdentifiableFXMLElement {

    private ExpressionContext.Loadable rootLoadable;
    private ClassElement superType;

    public RootFXMLElement(FXMLElement<?> parent) {
        super(parent);
    }

    @Override
    public void initialize(CompilerContext context) {
        super.initialize(context);

        if (getParent() != null) {
            throw context.compileError(FXMLLoader.FX_NAMESPACE_PREFIX + ":" + FXMLLoader.ROOT_TAG
                    + " is only valid as the root node of a document"
            );
        }

        rootLoadable = context.declareFxRootElement()
                .orElseThrow(() ->
                        context.compileError(
                                FXMLLoader.FX_NAMESPACE_PREFIX + ":" + FXMLLoader.ROOT_TAG + " defined, but no "
                                        + ProcessingInstructions.MLFX_ROOT_TYPE
                                        + " processing instruction present."
                        )
                );
    }

    @Override
    public void handleAttributeLookahead(CompilerContext context, String prefix, String localName, String value) {
        // NB: Lookahead handling logic is still required, as parent may or may not require attributes lookahead.
        if ((prefix == null || prefix.isEmpty()) && FXMLLoader.ROOT_TYPE_ATTRIBUTE.equals(localName)) {
            return;
        }

        super.handleAttributeLookahead(context, prefix, localName, value);
    }

    @Override
    public boolean requiresAttributesLookahead() {
        // No explicit lookahead required, as actual root element type is specified using processing instruction.
        return super.requiresAttributesLookahead();
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        if ((prefix == null || prefix.isEmpty()) && FXMLLoader.ROOT_TYPE_ATTRIBUTE.equals(localName)) {
            this.superType = context.getFXMLClassElement(value)
                    .orElseThrow(() -> context.compileError("Invalid type \"" + value + "\"."));
            return;
        }

        super.handleAttribute(context, prefix, localName, value);
    }

    @Override
    public void handleAttributesFinish(CompilerContext context) {
        if (superType == null) {
            throw context.compileError(FXMLLoader.ROOT_TYPE_ATTRIBUTE + " attribute is required.");
        }

        if (!ElementUtils.isAssignable(rootLoadable.getClassElement(), superType)) {
            throw context.compileError("Incompatible root types: "
                    + ElementUtils.getSimpleName(rootLoadable.getClassElement())
                    + " is not assignable to " + ElementUtils.getSimpleName(superType) + "."
            );
        }
    }

    @Override
    public ClassElement getClassElement() {
        return rootLoadable.getClassElement();
    }

    @Override
    public CompilerContext.RenderCommand load() {
        return rootLoadable.load();
    }
}