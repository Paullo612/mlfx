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

import io.github.paullo612.mlfx.api.internal.MLFXLoaderDelegate;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Loader of AOT compiled FXML file.
 *
 * <p>This was designed as drop-in replacement for {@code javafx-fxml} FXMLLoader class.</p>
 *
 * @author Paullo612
 */
public class MLFXLoader {

    private static final MLFXLoaderDelegate DELEGATE = MLFXLoaderDelegate.create();

    /**
     * Loads AOT compiled FXML file from specified fxml file location.
     *
     * @param location location of FXML file
     * @return instance of document's root element type
     * @param <R> document's root element type
     *
     * @throws IOException in case of load failure
     */
    public static <R> R load(URL location) throws IOException {
        return new MLFXLoader(location).load();
    }

    /**
     * Loads AOT compiled FXML file from specified fxml file location, applying specified resource bundle.
     *
     * @param location location of FXML file
     * @param resources resource bundle
     * @return instance of document's root element type
     * @param <R> document's root element type
     *
     * @throws IOException in case of load failure
     */
    public static <R> R load(URL location, ResourceBundle resources) throws IOException {
        return new MLFXLoader(location, resources).load();
    }

    /**
     * Loads AOT compiled FXML file from specified fxml file location, applying specified resource bundle and using
     * specified controller factory.
     *
     * @param location location of FXML file
     * @param resources resource bundle
     * @param controllerFactory controller factory
     * @return instance of document's root element type
     * @param <R> document's root element type
     *
     * @throws IOException in case of load failure
     */
    public static <R> R load(URL location, ResourceBundle resources, Callback<Class<?>, Object> controllerFactory)
            throws IOException {
        return new MLFXLoader(location, resources, controllerFactory).load();
    }

    private static class ControllerAccessorDelegate<C> implements ControllerAccessor<C> {

        private final ControllerAccessor<C> source;
        private final Callback<Class<?>, Object> controllerFactory;

        private ControllerAccessorDelegate(ControllerAccessor<C> source, Callback<Class<?>, Object> controllerFactory) {
            this.source = Objects.requireNonNull(source);
            this.controllerFactory = Objects.requireNonNull(controllerFactory);
        }

        @Override
        public C newControllerInstance() throws CompiledLoadException {
            try {
                assert getControllerClass() != null;
                return getControllerClass().cast(controllerFactory.call(getControllerClass()));
            } catch (Exception e) {
                throw new CompiledLoadException("Failed to construct controller using controller factory.", e);
            }
        }

        @Override
        public void setField(C controller, String fieldName, Object value) throws CompiledLoadException {
            source.setField(controller, fieldName, value);
        }

        @Override
        public void executeMethod(C controller, String methodName, Object... arguments) throws CompiledLoadException {
            source.executeMethod(controller, methodName, arguments);
        }

        @Override
        public ExecutableMethod findMethod(C controller, String methodName, Class<?>... argumentTypes)
                throws CompiledLoadException {
            return source.findMethod(controller, methodName, argumentTypes);
        }

        @Override
        public Class<C> getControllerClass() {
            return source.getControllerClass();
        }
    }

    private URL location;
    private ResourceBundle resources;
    private Callback<Class<?>, Object> controllerFactory;

    private Object root;
    private Object controller;

    /**
     * Constructs new loader.
     */
    public MLFXLoader() {
        this(null);
    }

    /**
     * Constructs new loader for specified FXML file location.
     *
     * @param location location of FXML file
     */
    public MLFXLoader(URL location) {
        this(location, null);
    }

    /**
     * Constructs new loader for specified FXML file location, applying specified resource bundle.
     *
     * @param location location of FXML file
     * @param resources resource bundle
     */
    public MLFXLoader(URL location, ResourceBundle resources) {
        this(location, resources, null);
    }

    /**
     * Constructs new loader for specified FXML file location, applying specified resource bundle and using specified
     * controller factory.
     *
     * @param location location of FXML file
     * @param resources resource bundle
     * @param controllerFactory controller factory
     */
    public MLFXLoader(URL location, ResourceBundle resources, Callback<Class<?>, Object> controllerFactory) {
        this.location = location;
        this.resources = resources;
        this.controllerFactory = controllerFactory;
    }

    /**
     * Returns current FXML file location.
     *
     * @return current FXML file location or {@code null} if none currently specified
     */
    public URL getLocation() {
        return location;
    }

    /**
     * Sets new FXML file location.
     *
     * @param location FXML file location.
     */
    public void setLocation(URL location) {
        this.location = location;
    }

    /**
     * Returns current resource bundle.
     *
     * @return current resource bundle or {@code null} if none currently specified
     */
    public ResourceBundle getResources() {
        return resources;
    }

    /**
     * Sets new resource bundle.
     *
     * @param resources resource bundle
     */
    public void setResources(ResourceBundle resources) {
        this.resources = resources;
    }

    /**
     * Returns current controller factory.
     *
     * @return current controller factory or {@code null} if none currently specified
     */
    public Callback<Class<?>, Object> getControllerFactory() {
        return controllerFactory;
    }

    /**
     * Sets new controller factory.
     *
     * @param controllerFactory controller factory
     */
    public void setControllerFactory(Callback<Class<?>, Object> controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    /**
     * Sets new document's root type instance.
     *
     * @param root document's root type instance
     */
    public void setRoot(Object root) {
        this.root = root;
    }

    /**
     * Returns current document's root type instance.
     *
     * @return current document's root type instance or {@code null} if none currently specified
     * @param <R> document's root type
     */
    @SuppressWarnings("unchecked")
    public <R> R getRoot() {
        return (R) root;
    }

    /**
     * Sets new controller.
     *
     * @param controller controller
     */
    public void setController(Object controller) {
        this.controller = controller;
    }

    /**
     * Returns current controller.
     *
     * @return current controller or {@code null} if none currently specified
     * @param <C> controller type
     */
    @SuppressWarnings("unchecked")
    public <C> C getController() {
        return (C) controller;
    }

    /**
     * Loads AOT compiled FXML file.
     *
     * @return instance of document's root element type
     * @param <R> document's root element type
     *
     * @throws IOException in case of load failure
     */
    public <R> R load() throws IOException {
        if (location == null) {
            throw new IllegalStateException("Location is not set.");
        }

        URI uri;

        try {
            uri = location.toURI();
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed to convert URL \"" + location + "\" to URI.");
        }

        CompiledFXMLLoader<?, ?> loader = DELEGATE.getCompiledLoader(uri)
                .orElseThrow(
                        () -> new IOException("Failed to find compiled FXML loader for URL \"" + location + "\".")
                );

        ControllerAccessorFactory factory;

        if (controllerFactory != null) {
            factory = new ControllerAccessorFactory() {
                @Override
                public <C> ControllerAccessor<C> createControllerAccessor(Class<C> controllerClass) {
                    return new ControllerAccessorDelegate<>(
                            DELEGATE.createControllerAccessor(controllerClass), controllerFactory
                    );
                }
            };
        } else {
            factory = DELEGATE;
        }

        Result<?, ?> result;

        try {
            result = loader.load(factory, controller, root, resources);
        } catch (CompiledLoadException e) {
            throw new IOException("Failed to load compiled FXML file.", e);
        }

        this.controller = result.getController();
        this.root = result.getRootInstance();

        return getRoot();
    }
}
