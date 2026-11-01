package org.duckcatmine.apocalypse.death;

import java.util.ArrayList;
import java.util.List;

/** Slot states of one death bag; every transition is one-way except an unconfirmed withdrawal. */
public final class BagModel {
    private final SlotState[] states;

    public BagModel(SlotState[] states) {
        this.states = states.clone();
    }

    public int size() {
        return states.length;
    }

    public SlotState state(int slot) {
        return states[slot];
    }

    public boolean beginTake(int slot) {
        if (slot < 0 || slot >= states.length || states[slot] != SlotState.PRESENT) return false;
        states[slot] = SlotState.MOVING;
        return true;
    }

    public void confirmTake(int slot) {
        if (states[slot] == SlotState.MOVING) states[slot] = SlotState.TAKEN;
    }

    /** A withdrawal that never reached the player returns to the bag, or is lost if the bag expired meanwhile. */
    public void cancelTake(int slot, boolean bagExpired) {
        if (states[slot] == SlotState.MOVING) states[slot] = bagExpired ? SlotState.PURGED : SlotState.PRESENT;
    }

    /** Purges what is still in the bag; in-flight withdrawals are left to reconciliation. */
    public List<Integer> expire() {
        List<Integer> purged = new ArrayList<>();
        for (int slot = 0; slot < states.length; slot++) {
            if (states[slot] == SlotState.PRESENT) {
                states[slot] = SlotState.PURGED;
                purged.add(slot);
            }
        }
        return purged;
    }

    public boolean hasPresent() {
        for (SlotState state : states) if (state == SlotState.PRESENT) return true;
        return false;
    }

    public boolean settled() {
        for (SlotState state : states) if (state == SlotState.PRESENT || state == SlotState.MOVING) return false;
        return true;
    }
}
