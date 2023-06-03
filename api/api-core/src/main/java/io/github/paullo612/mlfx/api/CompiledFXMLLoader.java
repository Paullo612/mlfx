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

import java.net.URI;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Base class for AOT compiled FXML loaders.
 *
 * @param <R> document's root type
 * @param <C> controller type
 *
 * @author Paullo612
 */
public abstract class CompiledFXMLLoader<R, C> {

    /**
     * Current ABI version.
     */
    public static final int ABI_VERSION = 0;

    /**
     * Loads AOT compiled FXML file.
     *
     * @param controllerAccessorFactory factory for controller accessors
     * @param externalController controller specified by user or {@code null} if controller is specified on document's
     *                           root element
     * @param rootInstance instance of root element specified by user or {@code null} if document's root element is not
     *                     fx:root
     * @param resourceBundle resource bundle or {@code null}
     * @return load result
     *
     * @throws CompiledLoadException in case of load failure
     */
    public Result<R, C> load(
            ControllerAccessorFactory controllerAccessorFactory,
            Object externalController,
            Object rootInstance,
            ResourceBundle resourceBundle) throws CompiledLoadException {
        if (getABIVersion() > ABI_VERSION) {
            // We're not forward compatible. Sorry.
            throw new CompiledLoadException(
                    "Attempt to load CompiledFXMLLoader implementation with ABI " + getABIVersion()
                            + ", but there is CompiledFXMLLoader with ABI " + ABI_VERSION + " on classpath."
            );
        }

        if (requiresResourceBundle() && resourceBundle == null) {
            throw new CompiledLoadException("Resource bundle required, but none provided.");
        }

        Optional<Class<R>> optionalRootInstanceClass = getRootInstanceClass();

        R castRoot;

        if (optionalRootInstanceClass.isPresent()) {
            if (rootInstance == null) {
                throw new CompiledLoadException("Root element instance required, but none provided.");
            }

            Class<R> rootInstanceClass = optionalRootInstanceClass.get();

            castRoot = Optional.of(rootInstanceClass)
                    .filter(c -> c.isInstance(rootInstance))
                    .map(c -> c.cast(rootInstance))
                    .orElseThrow(() ->
                            new CompiledLoadException(
                                    "Incompatible root instance; Instance of \"" + rootInstanceClass.getSimpleName()
                                            + "\" is required."
                            )
                    );
        } else {
            if (rootInstance != null) {
                throw new CompiledLoadException(
                        "Root element instance provided, but document's root node is not fx:root."
                );
            }

            castRoot = null;
        }

        Optional<Class<C>> optionalControllerClass = getControllerClass();

        if (optionalControllerClass.isPresent()) {
            ControllerAccessor<C> accessor;
            C controllerInstance;

            Class<C> controllerClass = optionalControllerClass.get();
            boolean requiresExternalController = requiresExternalController();

            if (externalController != null) {
                if (!requiresExternalController) {
                    throw new CompiledLoadException(
                            "External controller provided, but there is fx:controller defined on document's root node."
                    );
                }

                controllerInstance = Optional.of(controllerClass)
                        .filter(c -> c.isInstance(externalController))
                        .map(c -> c.cast(externalController))
                        .orElseThrow(() ->
                                new CompiledLoadException(
                                        "Incompatible controller instance provided; Instance of \""
                                                + controllerClass.getSimpleName() + "\" is required."
                                )
                        );
                accessor = controllerAccessorFactory.createControllerAccessor(controllerClass);
            } else {
                if (requiresExternalController) {
                    throw new CompiledLoadException("External controller is required, but none specified.");
                }

                accessor = controllerAccessorFactory.createControllerAccessor(controllerClass);
                // Controller is required, but none provided. Create one. Create it directly if we're able to do so and
                //  there is no controller factory specified. Fallback to accessor otherwise.
                controllerInstance = canCreateController() && !controllerAccessorFactory.isBackedByControllerFactory()
                        ? createController()
                        : accessor.newControllerInstance();
            }

            return doLoad(controllerAccessorFactory, resourceBundle, castRoot, accessor, controllerInstance);
        } else if (externalController != null) {
            throw new CompiledLoadException(
                    "Controller instance provided, but there is no \"fx:controller\" attribute specifier on document's"
                            + " root element and no \"mlfxControllerType\" processing instruction in document."
            );
        }

        return doLoad(controllerAccessorFactory, resourceBundle, castRoot, null, null);
    }

    /**
     * Loads AOT compiled FXML file.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @param controllerAccessorFactory factory for controller accessors
     * @param resourceBundle resource bundle or {@code null}
     * @param rootInstance instance of root element specified by user or {@code null} if document's root element is not
     *                     fx:root
     * @param accessor controller accessor or {@code null} if there is no controller
     * @param controller controller specified by user or {@code null} if controller is specified on document's root
     *                   element
     * @return load result
     *
     * @throws CompiledLoadException in case of load failure
     */
    protected abstract Result<R, C> doLoad(
            ControllerAccessorFactory controllerAccessorFactory,
            ResourceBundle resourceBundle,
            R rootInstance,
            ControllerAccessor<C> accessor,
            C controller) throws CompiledLoadException;

    /**
     * Returns ABI version of loader.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return ABI version
     */
    public abstract int getABIVersion();

    /**
     * Returns original URI of compiled FXML file.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return original URI
     * @throws CompiledLoadException in case URI cannot be constructed
     */
    public abstract URI getURI() throws CompiledLoadException;

    /**
     * Whether this loader requires resource bundle.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return {@code true} if resource bundle is required, {@code false} otherwise
     */
    public abstract boolean requiresResourceBundle();

    /**
     * Whether this loader requires user specified controller.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return {@code true} if user specified controller is required, {@code false} otherwise
     */
    public abstract boolean requiresExternalController();

    /**
     * Returns {@code Class} of root type.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return {@code Class} of root type
     */
    public abstract Optional<Class<R>> getRootInstanceClass();

    /**
     * Returns {@code Class} of controller type.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return {@code Class} of controller type
     */
    public abstract Optional<Class<C>> getControllerClass();

    /**
     * Whether this loader requires user specified instance of root type.
     *
     * @return {@code true} if user specified instance of root type is required, {@code false} otherwise
     */
    public boolean requiresRootInstance() {
        return getRootInstanceClass().isPresent();
    }

    /**
     * Whether this loader can create new controller instance.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return {@code true} if new controller instance can be created by this loader, {@code false} otherwise
     */
    public boolean canCreateController() {
        return false;
    }

    /**
     * Creates controller.
     *
     * <p>Intended to be implemented by generated code.</p>
     *
     * @return new controller instance
     * @throws CompiledLoadException if there is no controller specified, or it does not have accessible no-args
     *         constructor
     */
    public C createController() throws CompiledLoadException {
        throw new CompiledLoadException(
                "There is no controller, or controller does not have accessible no-args constructor"
        );
    }

    /**
     * Creates {@link Result} instance.
     *
     * @param rootInstance document's root element
     * @param controller document's controller
     *
     * @return new {@link Result} instance
     */
    protected Result<R, C> createResult(R rootInstance, C controller) {
        return new Result<>() {

            @Override
            public R getRootInstance() {
                return rootInstance;
            }

            @Override
            public C getController() {
                return controller;
            }
        };
    }
}
