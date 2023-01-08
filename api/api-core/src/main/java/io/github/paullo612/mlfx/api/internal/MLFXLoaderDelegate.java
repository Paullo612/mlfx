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
package io.github.paullo612.mlfx.api.internal;

import io.github.paullo612.mlfx.api.CompiledFXMLLoader;
import io.github.paullo612.mlfx.api.ControllerAccessorFactory;
import io.github.paullo612.mlfx.api.internal.impl.MLFXLoaderDelegateImpl;

import java.net.URI;
import java.util.Optional;

/**
 * Delegate for loader of AOT compiled FXML file.
 *
 * @author Paullo612
 */
public interface MLFXLoaderDelegate extends ControllerAccessorFactory {

    /**
     * Creates delegate.
     *
     * @return new delegate
     */
    static MLFXLoaderDelegate create() {
        return new MLFXLoaderDelegateImpl();
    }

    /**
     * Retrieves {@link CompiledFXMLLoader} instance for specified FXML file location.
     *
     * @param location FXML file location
     * @return {@link CompiledFXMLLoader} instance for specified location
     */
    Optional<CompiledFXMLLoader<?, ?>> getCompiledLoader(URI location);
}
