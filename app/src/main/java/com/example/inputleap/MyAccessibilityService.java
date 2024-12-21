package com.example.inputleap;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class MyAccessibilityService extends AccessibilityService {
        private static MyAccessibilityService instance;

        @Override
        public void onCreate() {
            super.onCreate();
            instance = this;
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            // Handle accessibility events if needed
        }

        @Override
        public void onInterrupt() {
            // Handle interruption if needed
        }

        @Override
        protected void onServiceConnected() {
            super.onServiceConnected();
        }
        public static MyAccessibilityService getInstance() {
            return instance;
        }
    }



