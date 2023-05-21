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
import io.github.paullo612.mlfx.expression.Expressions;
import io.github.paullo612.mlfx.expression.TypedContinuation;
import io.micronaut.inject.ast.ClassElement;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Type;

//NB: In theory, this one can be a root element, as it can copy one of its children (looks insane but still possible).
// We won't support this use case for now.
public class ReferenceFXMLElement extends IdentifiableFXMLElement {

    private TypedContinuation continuation;

    public ReferenceFXMLElement(FXMLElement<?> parent) {
        super(parent);
    }

    @Override
    public void handleAttributeLookahead(CompilerContext context, String prefix, String localName, String value) {
        if ((prefix == null || prefix.isEmpty()) && FXMLLoader.REFERENCE_SOURCE_ATTRIBUTE.equals(localName)) {
            TypedContinuation continuation = Expressions.expression(context, value);

            if (!continuation.canBeRun()) {
                throw context.compileError("Unable to evaluate expression \"" + value + "\".");
            }

            this.continuation = continuation;
            acquireSlot(context);

            return;
        }

        if (super.requiresAttributesLookahead()) {
            super.handleAttributeLookahead(context, prefix, localName, value);
        }
    }

    @Override
    public void handleAttributesLookaheadFinish(CompilerContext context) {
        if (continuation == null) {
            throw context.compileError(FXMLLoader.REFERENCE_SOURCE_ATTRIBUTE + " attribute is required.");
        }

        ClassElement classElement = getClassElement();

        ExpressionContext.RenderCommand renderCommand = continuation.run();

        // Save expression evaluation result to local variable.
        context.getRenderer().render(methodVisitor -> {
            Type objectType = RenderUtils.type(classElement);
            renderCommand.render(methodVisitor);
            methodVisitor.storeLocal(getSlot(), objectType);
        });
    }

    @Override
    public boolean requiresAttributesLookahead() {
        // NB: We use attributes lookahead here, as actual element type can only be determined from attribute.
        return true;
    }

    @Override
    public void handleAttribute(CompilerContext context, String prefix, String localName, String value) {
        if ((prefix == null || prefix.isEmpty()) && FXMLLoader.REFERENCE_SOURCE_ATTRIBUTE.equals(localName)) {
            return;
        }

        // NB: Looks strange, but we can add additional attribute values to reference's source.
        super.handleAttribute(context, prefix, localName, value);
    }

    @Override
    public CompilerContext.RenderCommand load() {
        return methodVisitor -> methodVisitor.loadLocal(getSlot());
    }

    @Override
    public ClassElement getClassElement() {
        return continuation.getType();
    }
}
