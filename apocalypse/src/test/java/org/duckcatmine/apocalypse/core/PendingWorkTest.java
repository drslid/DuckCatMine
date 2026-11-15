package org.duckcatmine.apocalypse.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingWorkTest {
    @Test void reentrantChunkLoadsRemainQueuedAndNeverInvalidateTheBatch() {
        var pending=new LinkedHashSet<>(List.of(1,2,3)); var handled=new ArrayList<Integer>();
        PendingWork.drain(pending,2,8,key -> { handled.add(key); pending.add(4); return true; });
        assertEquals(List.of(1,2),handled); assertEquals(List.of(3,4),List.copyOf(pending));
        PendingWork.drain(pending,2,8,key -> { handled.add(key); return true; });
        assertEquals(List.of(1,2,3,4),handled); assertTrue(pending.isEmpty());
    }
    @Test void unloadedChunksCannotCauseAnUnboundedMainThreadScan() {
        var pending=new LinkedHashSet<Integer>(); for (int i=0;i<100;i++) pending.add(i);
        var calls=new ArrayList<Integer>();
        PendingWork.drain(pending,2,8,key -> { calls.add(key); return false; });
        assertEquals(8,calls.size()); assertEquals(92,pending.size());
    }
    @Test void workRemovedByACallbackIsNotProcessedAgain() {
        var pending=new LinkedHashSet<>(List.of(1,2,3)); var handled=new ArrayList<Integer>();
        PendingWork.drain(pending,2,8,key -> { handled.add(key); pending.remove(2); return true; });
        assertEquals(List.of(1,3),handled); assertTrue(pending.isEmpty());
    }
}
