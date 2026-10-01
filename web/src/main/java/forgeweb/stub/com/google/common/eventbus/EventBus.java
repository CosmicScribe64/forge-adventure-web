package forgeweb.stub.com.google.common.eventbus;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Web stand-in for Guava's EventBus (forgeweb.ForgeWebSubstitutionPolicy). Guava finds handlers
 * by their @Subscribe annotation, which TeaVM doesn't keep, so every Forge game and UI event was
 * silently dropped: the duel screen never updated (empty hand, library, log) though the game ran.
 *
 * Here a handler is any public one-argument method named receive... or recieve... (Forge's
 * @Subscribe methods are all named so), called when the event is an instance of its parameter.
 * forgeweb.teavm.WebReflection exposes exactly those methods of Forge's listener classes.
 * Like Guava's default dispatcher, events posted while dispatching are delivered afterwards.
 */
public class EventBus {
    private final String identifier;
    private final List<Object> listeners = new ArrayList<>();
    private final ArrayDeque<Object> queue = new ArrayDeque<>();
    private boolean dispatching;

    public EventBus() {
        this("default");
    }

    public EventBus(String identifier) {
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }

    public void register(Object listener) {
        listeners.add(listener);
    }

    public void unregister(Object listener) {
        listeners.remove(listener);
    }

    public void post(Object event) {
        queue.add(event);
        if (dispatching) return;
        dispatching = true;
        try {
            Object next;
            while ((next = queue.poll()) != null) {
                for (Object listener : new ArrayList<>(listeners)) {
                    deliver(listener, next);
                }
            }
        } finally {
            dispatching = false;
        }
    }

    private static void deliver(Object listener, Object event) {
        for (Method m : listener.getClass().getMethods()) {
            String name = m.getName();
            if (!name.startsWith("receive") && !name.startsWith("recieve")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || !params[0].isInstance(event)) continue;
            try {
                m.invoke(listener, event);
            } catch (Exception e) {
                // Guava logs subscriber exceptions and carries on.
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.err.println("EventBus: " + listener.getClass().getName() + "." + name + " failed: " + cause);
                cause.printStackTrace();
            }
        }
    }
}
