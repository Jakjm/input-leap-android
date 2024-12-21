package com.example.inputleap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import java.util.concurrent.Executor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private View view;

    enum STATE {
            DISCONNECTED,
        CONNECTED
    };
    STATE state = STATE.DISCONNECTED;
    Socket socket;
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    BufferedReader reader;
    static {
        System.loadLibrary("inputleap");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try{
                    writeOutput();
                }
                catch (Exception e) {
                    // TODO: handle exception
                }
                finally{
                    //also call the same runnable to call it at regular interval
                    handler.postDelayed(this, 1000);
                }
            }
        };

        //runnable must be execute once
        handler.post(runnable);

    }
    boolean validateIp(String ip){
        int firstDot = ip.indexOf('.');
        int secondDot = ip.indexOf('.', firstDot + 1);
        int thirdDot = ip.indexOf('.', secondDot + 1);
        if( firstDot == -1 || secondDot == -1 || thirdDot == - 1 ) {
            return false;
        }
        else if(secondDot - firstDot <= 1 || thirdDot - secondDot <= 1){
            return false;
        }
        try {
            int[] bytes = new int[4];
            bytes[0] = Integer.parseInt(ip.substring(0, firstDot));
            bytes[1] = Integer.parseInt(ip.substring(firstDot + 1, secondDot));
            bytes[2] = Integer.parseInt(ip.substring(secondDot + 1,thirdDot));
            bytes[3] = Integer.parseInt(ip.substring(thirdDot + 1));

            for(int i = 0; i < bytes.length;++i){
                if(bytes[i] < 0 || bytes[i] > 255)return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
    public STATE connect(String ip){
        STATE result;
        Callable<STATE> callable = new Callable<STATE>(){
            public STATE call() {
                STATE result = STATE.CONNECTED;
                try {
                    SocketFactory sslFactory = SSLSocketFactory.getDefault();
                    socket = sslFactory.createSocket(ip, 24800);
                    socket.setSoTimeout(200);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                } catch (Exception e) {
                    result = STATE.DISCONNECTED;
                }
                return result;
            }
        };
        FutureTask<STATE> task = new FutureTask<STATE>(callable);
        try {

            executorService.execute(task);
            result = task.get(150, TimeUnit.MILLISECONDS);
        }
        catch(Exception e){
            return STATE.DISCONNECTED;
        }
        return result;
    }

    public void disconnect(){

    }

    public void writeOutput(){
        try {
            if(socket != null){
                EditText outputText = (EditText)findViewById(R.id.editTextTextMultiLine);
                int output = reader.read();
                outputText.setText(outputText.getText().append((char)output));
            }
        }
        catch(Exception e){
            Exception x = e;
        }
    }

    @Override
    public void onClick(View v) {
        Button startClientBtn = (Button)findViewById(R.id.start_client_button);
        TextInputEditText ipText = (TextInputEditText)findViewById(R.id.ip_text);

        if(state == STATE.DISCONNECTED){
            Editable editable = ipText.getText();
            if(editable != null) {
                String ip = editable.toString();
                if(validateIp(ip)) {
                    state = connect(ip);
                    if (state == STATE.CONNECTED) {
                        startClientBtn.setText("Stop InputLeap Client");
                        ipText.setEnabled(false);
                    }
                }
            }
        }
        else{

            disconnect();
            state = STATE.DISCONNECTED;
            startClientBtn.setText("Start InputLeap Client");
            ipText.setEnabled(true);
        }

    }

    public void onMoveMouseClick(View view) {
        this.view = view;
        moveMouseInCircle();
    }

    private void moveMouseInCircle() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            Path path = new Path();
            int centerX = 500; // Center X coordinate
            int centerY = 500; // Center Y coordinate
            int radius = 100; // Radius of the circle

            for (int i = 0; i <= 360; i += 10) {
                double angle = Math.toRadians(i);
                float x = (float) (centerX + radius * Math.cos(angle));
                float y = (float) (centerY + radius * Math.sin(angle));
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1000));
            GestureDescription gesture = gestureBuilder.build();

            dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    // Handle completion
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    // Handle cancellation
                }
            }, null);
        }
    }

    @SuppressLint("ServiceCast")
    private void dispatchGesture(GestureDescription gesture, AccessibilityService.GestureResultCallback gestureResultCallback, Object o) {
        ((AccessibilityService) getSystemService(ACCESSIBILITY_SERVICE)).dispatchGesture(gesture, gestureResultCallback, null);
    }

}