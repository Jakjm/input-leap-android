package com.example.inputleap;

import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.CheckBox;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import javax.net.ssl.*;
import com.google.android.material.textfield.TextInputEditText;

import org.synergy.base.utils.Log;
import org.synergy.client.Client;
import org.synergy.common.screens.BasicScreen;
import org.synergy.net.InputLeapTrustManager;
import org.synergy.net.SocketFactoryInterface;
import org.synergy.net.TCPSocketFactory;
import org.synergy.net.TLSSocketFactory;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String PROP_clientName = "clientNAME";
    private final static String PROP_serverPort = "serverPORT";
    private final static String PROP_serverURL = "serverURL";
    private final static String PROP_enableSSL = "enableSSL";
    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;

    AtomicInteger state = new AtomicInteger(DISCONNECTED);

    //Socket socket;
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    //BufferedReader reader;

    //XML elements from screen....
    Button startClientBtn;
    TextInputEditText ipText;
    TextInputEditText portText;
    TextInputEditText clientNameText;
    CheckBox enable_ssl_checkbox;

    MainLoopThread mainLoopThread;

    static {
        System.loadLibrary("inputleap");
    }

    void loadElements(){ //Load elements of our activity....
        startClientBtn = (Button)findViewById(R.id.start_client_button);
        ipText = (TextInputEditText)findViewById(R.id.ip_EditText);
        clientNameText = (TextInputEditText)findViewById(R.id.client_name_EditText);
        portText = (TextInputEditText)findViewById(R.id.port_EditText);
        enable_ssl_checkbox = (CheckBox)findViewById(R.id.enable_ssl_checkbox);
    }

    void initElements(){ //Initialize elements of our activity with the correct text...
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        ipText.setText(preferences.getString(PROP_serverURL,""));
        portText.setText(preferences.getString(PROP_serverPort, "24800"));
        clientNameText.setText(preferences.getString(PROP_clientName, android.os.Build.MODEL));
        enable_ssl_checkbox.setChecked(preferences.getBoolean(PROP_enableSSL, true));
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

        loadElements();
        initElements();

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateConnectionStatus();
                } catch (Exception e) {
                    // TODO: handle exception
                } finally {
                    //also call the same runnable to call it at regular interval
                    handler.postDelayed(this, 200);
                }
            }
        };

        //runnable must be execute once
        handler.post(runnable);

        mainLoopThread = new MainLoopThread();

    }

    public void updateConnectionStatus() throws IOException {
        int status = state.get();
        if(status == CONNECTED){
            startClientBtn.setEnabled(true);
            startClientBtn.setText(R.string.stop_inputleap_client);
        }
        else if(status == CONNECTING){
            startClientBtn.setText(R.string.connecting_please_wait);

        }
        else{
            startClientBtn.setEnabled(true);
            ipText.setEnabled(true);
            portText.setEnabled(true);
            clientNameText.setEnabled(true);
            enable_ssl_checkbox.setEnabled(true);
            startClientBtn.setText(R.string.start_inputleap_client);
        }
    }


    public void updatePreferences(String name, String url, int port, boolean isChecked){
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();
        preferencesEditor.putString(PROP_clientName, name);
        preferencesEditor.putString(PROP_serverURL, url);
        preferencesEditor.putString(PROP_serverPort, Integer.toString(port));
        preferencesEditor.putBoolean(PROP_enableSSL, isChecked);
        preferencesEditor.apply();
    }


    public Client createClient(InetSocketAddress addressPort, String clientName){
        //SocketFactoryInterface socketFactory = new TCPSocketFactory();

        // TODO start the accessibility service injection here

        BasicScreen basicScreen = new BasicScreen();
        WindowManager wm = getWindowManager();
        Display display = wm.getDefaultDisplay();
        basicScreen.setShape(display.getWidth(), display.getHeight());
        Log.debug("Resolution: " + display.getWidth() + " x " + display.getHeight());

        //PlatformIndependentScreen screen = new PlatformIndependentScreen(basicScreen);
        Log.debug("Hostname: " + clientName);

        //TODO... add this back in
        Client client = new Client(getApplicationContext(), clientName, addressPort, basicScreen);
        return client;
    }

    @Override
    public void onClick(View v) {
        int status = state.get();
        if(status == CONNECTED){
            //disconnect();
            state.set(DISCONNECTED);
        }
        else {
            startClientBtn.setEnabled(false);
            ipText.setEnabled(false);
            portText.setEnabled(false);
            clientNameText.setEnabled(false);
            enable_ssl_checkbox.setEnabled(false);
            try {
                String ip = ipText.getText().toString();
                int port = Integer.parseInt(portText.getText().toString());
                String clientName = clientNameText.getText().toString();
                boolean useTLS = enable_ssl_checkbox.isChecked();

                InetSocketAddress addressPort = new InetSocketAddress(ip, port);
                updatePreferences(clientName, ip, port, useTLS);
                state.set(CONNECTING);

                SocketFactoryInterface socketFactory;
                if(useTLS)socketFactory = new TLSSocketFactory();
                else socketFactory = new TCPSocketFactory();
                //connect();
                Client client = createClient(addressPort, clientName);
                Runnable connectTask = new Runnable(){
                    public void run(){
                        client.connect(MainActivity.this, socketFactory);
                        state.set(CONNECTED);
                    }
                };
                executorService.execute(connectTask);
                mainLoopThread.startNewMainLoopThread();
            }
            catch(Exception e){
                //TODO complain that something is incorrect....
                state.set(DISCONNECTED);
            }
        }
    }

    @SuppressLint("ServiceCast")
    private void dispatchGesture(GestureDescription gesture, AccessibilityService.GestureResultCallback gestureResultCallback, Object o) {
        ((AccessibilityService) getSystemService(ACCESSIBILITY_SERVICE)).dispatchGesture(gesture, gestureResultCallback, null);
    }

}