package com.example.inputleap;

import org.synergy.base.Event;
import org.synergy.base.EventQueue;
import org.synergy.base.EventType;
import org.synergy.base.utils.Log;
public class MainLoopThread extends Thread {
    static MainLoopThread currentThread;
    public void run() {
        try {
            Event event = new Event();
            event = EventQueue.getInstance().getEvent(event, -1.0);
            Log.note("Event grabbed");
            while (event.getType() != EventType.QUIT && currentThread == Thread.currentThread()) {
                EventQueue.getInstance().dispatchEvent(event);
                // TODO event.deleteData ();
                event = EventQueue.getInstance().getEvent(event, -1.0);
                Log.note("Event grabbed");
            }
        } catch (Exception e) {
            Log.error("Exception in MainLoopThread");
        } finally {
            // TODO stop the accessibility injection service
            Log.note("MainLoopThread stopped");
            stopMainLoopThread();

        }
    }
    public void startNewMainLoopThread(){
        currentThread = new MainLoopThread();
        currentThread.start();
    }
    public void stopMainLoopThread(){
        currentThread = null;
    }
}


