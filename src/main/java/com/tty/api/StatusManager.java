package com.tty.api;

import com.tty.api.state.State;
import com.tty.api.state.StateService;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class StatusManager {

    private final Map<Class<? extends StateService<?>>, StateService<? extends State>> stateMachines = new HashMap<>();

    public <T extends StateService<? extends State>> void registerStateMachine(@Nullable List<T> machines) {
        if (machines == null || machines.isEmpty()) return;
        for (T machine : machines) {
            this.stateMachines.put((Class<? extends StateService<?>>) machine.getClass(), machine);
        }
    }

    public <T extends StateService<? extends State>> T get(Class<T> clazz) {
        StateService<? extends State> service = this.stateMachines.get(clazz);
        if (service == null) throw new NullPointerException("could not found service " + clazz.getName() + ".");
        return (T) this.stateMachines.get(clazz);
    }

    public synchronized void abort() {
        this.stateMachines.values().forEach(StateService::abort);
    }

    public synchronized void reload() {
        this.stateMachines.values().forEach(StateService::onReload);
    }

}
