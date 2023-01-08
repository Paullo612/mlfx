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
 * Accessor for controller fields and methods.
 *
 * @param <C> controller type
 *
 * @author Paullo612
 */
public interface ControllerAccessor<C> {

    /**
     * Represents controller method.
     *
     * @author Paullo612
     */
    interface ExecutableMethod {

        /**
         * Calls method without any arguments.
         */
        default void execute() {
            execute(new Object[] {});
        }

        /**
         * Calls method with one argument.
         *
         * @param argument1 argument value
         */
        default void execute(Object argument1) {
            execute(new Object[] {argument1});
        }

        /**
         * Calls method with two arguments.
         *
         * @param argument1 first argument value
         * @param argument2 second argument value
         */
        default void execute(Object argument1, Object argument2) {
            execute(new Object[] {argument1, argument2});
        }

        /**
         * Calls method with three arguments.
         *
         * @param argument1 first argument value
         * @param argument2 second argument value
         * @param argument3 third argument value
         */
        default void execute(Object argument1, Object argument2, Object argument3) {
            execute(new Object[] {argument1, argument2, argument3});
        }

        /**
         * Calls method with four arguments.
         *
         * @param argument1 first argument value
         * @param argument2 second argument value
         * @param argument3 third argument value
         * @param argument4 forth argument value
         */
        default void execute(Object argument1, Object argument2, Object argument3, Object argument4) {
            execute(new Object[] {argument1, argument2, argument3, argument4});
        }

        /**
         * Calls controller method.
         *
         * @param arguments arguments to pass
         */
        void execute(Object... arguments);
    }

    /**
     * Returns {@code Class} of controller type.
     *
     * @return {@code Class} of controller type
     */
    Class<C> getControllerClass();

    /**
     * Creates controller instance.
     *
     * @return new controller instance
     *
     * @throws CompiledLoadException in case controller cannot be created
     */
    C newControllerInstance() throws CompiledLoadException;

    /**
     * Sets controller field.
     *
     * @param controller controller instance
     * @param fieldName name of field to set
     * @param value field's new value
     *
     * @throws CompiledLoadException in case field does not exist or cannot be set
     */
    void setField(C controller, String fieldName, Object value) throws CompiledLoadException;

    /**
     * Calls controller method.
     *
     * @param controller controller instance
     * @param methodName name of method to call
     * @param arguments arguments to pass
     *
     * @throws CompiledLoadException in case method does not exist or cannot be called
     */
    void executeMethod(C controller, String methodName, Object... arguments) throws CompiledLoadException;

    /**
     * Searches for method in controller.
     *
     * @param controller controller instance
     * @param methodName name of method to search for
     * @param argumentTypes method's argument types
     * @return {@link ExecutableMethod} which can be used to call this method later
     *
     * @throws CompiledLoadException in case method does not exist
     */
    ExecutableMethod findMethod(C controller, String methodName, Class<?>... argumentTypes)
            throws CompiledLoadException;
}
