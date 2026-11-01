package org.duckcatmine.apocalypse.panel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Recent notable events of Ground Zero for the web panel journal (memory only, newest first). */
public final class EventLog {
    public record Event(long at, String kind, String text) {
    }

    private static final int SIZE = 60;
    private final Deque<Event> events = new ArrayDeque<>();

    public synchronized void add(String kind, String text) {
        events.addFirst(new Event(System.currentTimeMillis(), kind, text));
        while (events.size() > SIZE) events.removeLast();
    }

    public synchronized List<Event> entries() {
        return new ArrayList<>(events);
    }
}
