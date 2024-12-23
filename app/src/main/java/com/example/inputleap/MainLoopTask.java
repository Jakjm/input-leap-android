package com.example.inputleap;

import org.synergy.base.Event;
import org.synergy.base.EventQueue;
import org.synergy.base.EventType;
import org.synergy.base.utils.Log;

public class MainLoopTask implements Runnable {
    public void run() {
        try {
            Event event = new Event();
            event = EventQueue.getInstance().getEvent(event, -1.0);
            Log.note("Event grabbed");
            while (event.getType() != EventType.QUIT) {
                EventQueue.getInstance().dispatchEvent(event);
                // TODO event.deleteData ();
                event = EventQueue.getInstance().getEvent(event, -1.0);
                Log.note("Event grabbed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // TODO stop the accessibility injection service
        }
    }
}
