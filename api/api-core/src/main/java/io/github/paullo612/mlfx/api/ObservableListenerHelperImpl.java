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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

class ObservableListenerHelperImpl implements ObservableListenerHelper {

    private class ListenerData<T> implements ChangeListener<T> {
        private final List<Integer> steps = new ArrayList<>();
        private final ObservableValue<T> propertyModel;

        ListenerData(ObservableValue<T> propertyModel) {
            this.propertyModel = propertyModel;

            propertyModel.addListener(this);
        }

        void addStep(int step) {
            if (steps.contains(step)) {
                return;
            }

            steps.add(step);
        }

        boolean removeStep(int step) {
            return steps.remove((Integer) step);
        }

        boolean hasSteps() {
            return !steps.isEmpty();
        }

        ObservableValue<T> getPropertyModel() {
            return propertyModel;
        }

        @Override
        public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
            ListIterator<Integer> it = steps.listIterator(steps.size());

            while (it.hasPrevious()) {
                doUpdate(it.previous());
            }
        }

        void dispose() {
            propertyModel.removeListener(this);
        }
    }

    private final Map<Object, List<ListenerData<?>>> listenerData = new IdentityHashMap<>();
    private final ValueUpdater updater;
    private List<Integer> toUpdate;

    ObservableListenerHelperImpl(ValueUpdater updater) {
        this.updater = updater;
    }

    void doUpdate(Integer step) {
        if (toUpdate != null) {
            // Postpone update till listeners unlock.
            toUpdate.add(step);
            return;
        }

        updater.update(step);
    }

    @Override
    public void lockListeners() {
        toUpdate = new ArrayList<>();
    }

    @Override
    public void unlockListeners() {
        List<Integer> toUpdate = this.toUpdate;
        this.toUpdate = null;

        for (Integer step : toUpdate) {
            updater.update(step);
        }
    }

    @Override
    public void addListener(Object bean, ObservableValue<?> propertyModel, int step) {
        List<ListenerData<?>> listeners = listenerData.computeIfAbsent(bean, __ -> new ArrayList<>());

        ListenerData<?> data = null;

        for (ListenerData<?> currentData : listeners) {
            if (currentData.getPropertyModel() == propertyModel) {
                data = currentData;
                break;
            }
        }

        if (data == null) {
            data = new ListenerData<>(propertyModel);
            listeners.add(data);
        }

        data.addStep(step);
    }

    @Override
    public void removeListener(Object bean, int step) {
        List<ListenerData<?>> listeners = listenerData.get(bean);

        if (listeners == null) {
            return;
        }

        ListIterator<ListenerData<?>> it = listeners.listIterator();

        while (it.hasNext()) {
            ListenerData<?> currentData = it.next();

            if (currentData.removeStep(step)) {
                if (!currentData.hasSteps()) {
                    currentData.dispose();
                    it.remove();
                }

                if (listeners.isEmpty()) {
                    listenerData.remove(bean);
                }

                return;
            }
        }
    }
}
