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

import io.micronaut.inject.ast.ClassElement;
import javafx.beans.property.Property;

public interface BindingContext extends ExpressionContext {

    interface LoadableCapturer {

        Loadable capture(Loadable loadable);
    }

    interface Storable extends Loadable {

        RenderCommand store(RenderCommand command);
    }

    interface LoadableStorage {

        Storable store(ClassElement classElement);
    }

    interface FlagSet {

        interface Flag {

            RenderCommand check();

            RenderCommand set();

            RenderCommand clear();
        }

        Flag createFlag();
    }

    interface BindingExpressionRendererContext extends LoadableCapturer, LoadableStorage, FlagSet {

        Loadable getListenerHelper();
    }

    interface BindingExpressionRenderer extends BindingExpressionRendererContext {

        void render(ExpressionContext.RenderCommand command);

        RenderCommand newInstance();
    }

    BindingExpressionRenderer createExpressionRenderer(
            String expression,
            Class<? extends Property> parentClass,
            ClassElement genericType
    );
}
