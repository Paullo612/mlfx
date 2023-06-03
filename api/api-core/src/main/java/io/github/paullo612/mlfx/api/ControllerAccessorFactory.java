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
package io.github.paullo612.mlfx.api;

/**
 * Factory for controller accessors.
 *
 * @author Paullo612
 */
public interface ControllerAccessorFactory {

    /**
     * Creates controller accessor.
     *
     * @param controllerClass {@code Class} of controller type
     * @return new controller accessor instance
     *
     * @param <C> controller type
     */
    <C> ControllerAccessor<C> createControllerAccessor(Class<C> controllerClass);

    /**
     * Indicates that this controller accessor factory is backed by controller factory.
     *
     * <p>If factory is backed by controller factory, controller factory is used to construct new controller
     * instances. Otherwise controller is constructed by controller accessor directly.</p>
     *
     * @return {@code true} if this factory is backed by controller factory, {@code false} if it is not
     */
    default boolean isBackedByControllerFactory() {
        return false;
    }
}
