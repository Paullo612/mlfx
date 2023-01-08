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

import javafx.beans.value.ObservableValue;

/**
 * Helper class for managing {@link ObservableValue}'s listeners.
 *
 * <p>Intended to be used by generated code.</p>
 *
 * @author Paullo612
 */
public interface ObservableListenerHelper {

    /**
     * Creates observable listener helper.
     *
     * @param valueUpdater {@link ValueUpdater} to notify about observable values changes
     * @return new observable listener helper
     */
    static ObservableListenerHelper newInstance(ValueUpdater valueUpdater) {
        return new ObservableListenerHelperImpl(valueUpdater);
    }

    /**
     * Observable value change notification receiver.
     *
     * @author Paullo612
     */
    interface ValueUpdater {

        /**
         * Called on observable value change.
         *
         * @param step step index that represents listener that was notified about value change
         */
        void update(int step);
    }

    /**
     * Locks listeners.
     *
     * <p>While listeners are locked, {@code ValueUpdater}'s update method is not notified about value changes.</p>
     */
    void lockListeners();

    /**
     * Unlocks listeners.
     *
     * <p>While listeners are unlocked, {@code ValueUpdater}'s update method is notified about value changes.</p>
     */
    void unlockListeners();

    /**
     * Adds new observable value listener.
     *
     * @param bean property's bean
     * @param propertyModel property model
     * @param step step index to be passed to {@code ValueUpdater}'s update method
     */
    void addListener(Object bean, ObservableValue<?> propertyModel, int step);

    /**
     * Removes observable value listener represented by step index.
     *
     * @param bean property's bean
     * @param step step index that represents listener
     */
    void removeListener(Object bean, int step);
}
