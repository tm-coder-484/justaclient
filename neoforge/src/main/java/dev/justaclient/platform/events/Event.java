package dev.justaclient.platform.events;
import java.util.*;
public final class Event<T> {
    private final List<T> listeners = new ArrayList<>();
    public void register(T listener) { listeners.add(listener); }
    public List<T> listeners() { return List.copyOf(listeners); }
}
