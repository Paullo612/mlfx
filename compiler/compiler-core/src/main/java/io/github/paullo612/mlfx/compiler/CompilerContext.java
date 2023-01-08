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
package io.github.paullo612.mlfx.compiler;

import io.github.paullo612.mlfx.expression.BindingContext;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Optional;

public interface CompilerContext extends BindingContext {

    interface Renderer {

        void render(ExpressionContext.RenderCommand command);
    }

    @Override
    CompileErrorException compileError(String message);

    void warn(String message);

    void warn(String message, Element element);

    ClassElement getTargetType();

    Optional<Loadable> declareFxRootElement();

    Loadable declareFxController(ClassElement classElement);

    Optional<ClassElement> getFXMLClassElement(String name);

    void setControllerField(String name, Loadable value);

    Loadable getNonRequiredResourceBundle();

    Loadable getControllerAccessor();

    Loadable getControllerAccessorFactory();

    Charset getCharset();

    Optional<CompileTask> getCompileTask(URI location);

    Renderer getRenderer();

    void setRenderer(Renderer renderer);

    int getDefaultSlot();

    int acquireSlot();

    void releaseSlot(int slot);
}
