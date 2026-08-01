package com.example.csgocompat.client;

import com.example.csgocompat.network.KillFeedPayload;

import java.util.ArrayDeque;
import java.util.Deque;

/** Righe del kill feed lato client, con scadenza automatica. */
public final class KillFeed {

    private static final int MAX_ENTRIES = 5;
    private static final long LIFETIME_MILLIS = 6000;

    public record Entry(KillFeedPayload payload, long shownAt) {
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private KillFeed() {
    }

    public static void add(KillFeedPayload payload) {
        ENTRIES.addFirst(new Entry(payload, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeLast();
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static Iterable<Entry> visible() {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(entry -> now - entry.shownAt() > LIFETIME_MILLIS);
        return ENTRIES;
    }

    public static boolean isEmpty() {
        return !visible().iterator().hasNext();
    }
}
