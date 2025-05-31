package com.example.inputleap;

import com.example.inputleap.R; // Explicitly import project's R class

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import org.synergy.base.utils.Log;
import org.synergy.client.Client;
import org.synergy.common.screens.BasicScreen;
import org.synergy.net.SocketFactoryInterface;
import org.synergy.net.TCPSocketFactory;
import org.synergy.net.TLSSocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String PROP_clientName = "clientNAME";
    private final static String PROP_serverPort = "serverPORT";
    private final static String PROP_serverURL = "serverURL";
    private final static String PROP_enableSSL = "enableSSL";
    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;

    private static final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    AtomicInteger state = new AtomicInteger(DISCONNECTED);

    BluetoothAdapter bluetoothAdapter;
    ExecutorService executorService = Executors.newFixedThreadPool(4);

    Button startClientBtn;
    TextInputEditText ipText;
    TextInputEditText portText;
    TextInputEditText clientNameText;
    CheckBox enable_ssl_checkbox;
    RadioGroup connection_type_RadioGroup;
    RadioButton tcp_ip_RadioButton;
    RadioButton bluetooth_RadioButton;
    LinearLayout tcp_ip_settings_Layout;

    MainLoopThread mainLoopThread;

    private final ArrayList<BluetoothDevice> discoveredDevicesList = new ArrayList<>();
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private ActivityResultLauncher<String[]> requestBluetoothPermissionsLauncher;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;

    private BluetoothSocket mConnectedBluetoothSocket = null;
    private boolean isBluetoothConnected = false;

    static {
        System.loadLibrary("inputleap");
    }

    void loadElements() {
        startClientBtn = findViewById(R.id.start_client_button);
        ipText = findViewById(R.id.ip_EditText);
        clientNameText = findViewById(R.id.client_name_EditText);
        portText = findViewById(R.id.port_EditText);
        enable_ssl_checkbox = findViewById(R.id.enable_ssl_checkbox);
        connection_type_RadioGroup = findViewById(R.id.connection_type_RadioGroup);
        tcp_ip_RadioButton = findViewById(R.id.tcp_ip_RadioButton);
        bluetooth_RadioButton = findViewById(R.id.bluetooth_RadioButton);
        tcp_ip_settings_Layout = findViewById(R.id.tcp_ip_settings_Layout);
    }

    void initElements() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        ipText.setText(preferences.getString(PROP_serverURL, ""));
        portText.setText(preferences.getString(PROP_serverPort, "24800"));
        clientNameText.setText(preferences.getString(PROP_clientName, android.os.Build.MODEL));
        enable_ssl_checkbox.setChecked(preferences.getBoolean(PROP_enableSSL, true));

        connection_type_RadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.tcp_ip_RadioButton) {
                tcp_ip_settings_Layout.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.bluetooth_RadioButton) {
                tcp_ip_settings_Layout.setVisibility(View.GONE);
            }
        });

        if (tcp_ip_RadioButton.isChecked()) {
            tcp_ip_settings_Layout.setVisibility(View.VISIBLE);
        } else {
            tcp_ip_settings_Layout.setVisibility(View.GONE);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
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

        requestBluetoothPermissionsLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean allRequiredPermissionsGranted = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Boolean scanGranted = result.get(Manifest.permission.BLUETOOTH_SCAN);
                        Boolean connectGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
                        allRequiredPermissionsGranted = Boolean.TRUE.equals(scanGranted) && Boolean.TRUE.equals(connectGranted);
                    }

                    if (allRequiredPermissionsGranted) {
                        Log.debug("Bluetooth permissions granted.");
                        proceedWithBluetoothConnection();
                    } else {
                        Log.debug("Bluetooth permissions denied.");
                        showErrorDialog(getString(R.string.bluetooth_permission_error_title), getString(R.string.bluetooth_permission_error_message_scan_connect));
                        state.set(DISCONNECTED);
                        updateConnectionStatusOnUiThread();
                    }
                });

        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Log.debug("Bluetooth enabled by user.");
                        proceedWithBluetoothConnection();
                    } else {
                        Log.debug("Bluetooth not enabled by user or error.");
                        showErrorDialog(getString(R.string.bluetooth_required_title), getString(R.string.bluetooth_required_message_not_enabled));
                        state.set(DISCONNECTED);
                        updateConnectionStatusOnUiThread();
                    }
                });

        initElements();

        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateConnectionStatus();
                } catch (IOException e) {
                    Log.error("Error updating connection status in periodic handler: " + e.getMessage());
                } finally {
                    handler.postDelayed(this, 200);
                }
            }
        };

        handler.post(runnable);

        mainLoopThread = new MainLoopThread();
    }

    public void updateConnectionStatus() throws IOException {
        int status = state.get();
        if (status == CONNECTED) {
            startClientBtn.setEnabled(true);
            startClientBtn.setText(R.string.stop_inputleap_client);
            ipText.setEnabled(false);
            portText.setEnabled(false);
            clientNameText.setEnabled(false);
            enable_ssl_checkbox.setEnabled(false);
            connection_type_RadioGroup.setEnabled(false);
        } else if (status == CONNECTING) {
            startClientBtn.setEnabled(false);
            ipText.setEnabled(false);
            portText.setEnabled(false);
            clientNameText.setEnabled(false);
            enable_ssl_checkbox.setEnabled(false);
            connection_type_RadioGroup.setEnabled(false);
        } else {
            startClientBtn.setEnabled(true);
            startClientBtn.setText(R.string.start_inputleap_client);
            connection_type_RadioGroup.setEnabled(true);
            if (tcp_ip_RadioButton.isChecked()) {
                tcp_ip_settings_Layout.setVisibility(View.VISIBLE);
                ipText.setEnabled(true);
                portText.setEnabled(true);
                clientNameText.setEnabled(true);
                enable_ssl_checkbox.setEnabled(true);
            } else {
                tcp_ip_settings_Layout.setVisibility(View.GONE);
                ipText.setEnabled(false);
                portText.setEnabled(false);
                clientNameText.setEnabled(false);
                enable_ssl_checkbox.setEnabled(false);
            }
        }
    }

    public void updatePreferences(String name, String url, int port, boolean isChecked) {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();
        preferencesEditor.putString(PROP_clientName, name);
        preferencesEditor.putString(PROP_serverURL, url);
        preferencesEditor.putString(PROP_serverPort, Integer.toString(port));
        preferencesEditor.putBoolean(PROP_enableSSL, isChecked);
        preferencesEditor.apply();
    }

    public Client createClient(InetSocketAddress addressPort, String clientName) {
        BasicScreen basicScreen = new BasicScreen();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display.getRealSize(size);
        } else {
            display.getSize(size);
        }
        basicScreen.setShape(size.x, size.y);
        Log.debug("Resolution: " + size.x + " x " + size.y);
        Log.debug("Hostname: " + clientName);
        return new Client(getApplicationContext(), clientName, addressPort, basicScreen);
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo serviceInfo : enabledServices) {
            if (serviceInfo.getId().equals(getPackageName() + "/.MyAccessibilityService")) {
                return true;
            }
        }
        return false;
    }

    private void promptToEnableAccessibilityService() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.accessibility_service_required_title)
                .setMessage(R.string.accessibility_service_required_message)
                .setPositiveButton(R.string.enable_now, (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    showErrorDialog(getString(R.string.control_disabled_title), getString(R.string.control_disabled_message_no_service));
                    state.set(DISCONNECTED);
                    updateConnectionStatusOnUiThread();
                })
                .show();
    }

    @Override
    public void onClick(View v) {
        int currentStatus = state.get();
        if (currentStatus == CONNECTED) {
            state.set(DISCONNECTED);
            if (isBluetoothConnected) {
                if (mConnectedBluetoothSocket != null) {
                    try {
                        mConnectedBluetoothSocket.close();
                        Log.info("Bluetooth socket closed by user.");
                    } catch (IOException e) {
                        Log.error("Error closing Bluetooth socket: " + e.getMessage());
                    }
                    mConnectedBluetoothSocket = null;
                }
                isBluetoothConnected = false;
                Log.info("Bluetooth connection stopped by user.");
            } else {
                Log.info("TCP/IP connection stop initiated by user (actual client stop needs implementation).");
            }
            updateConnectionStatusOnUiThread();
        } else if (currentStatus == DISCONNECTED) {
            startClientBtn.setEnabled(false);
            connection_type_RadioGroup.setEnabled(false);

            if (tcp_ip_RadioButton.isChecked()) {
                if (!isAccessibilityServiceEnabled()) {
                    promptToEnableAccessibilityService();
                    startClientBtn.setEnabled(true);
                    connection_type_RadioGroup.setEnabled(true);
                    return;
                }

                ipText.setEnabled(false);
                portText.setEnabled(false);
                clientNameText.setEnabled(false);
                enable_ssl_checkbox.setEnabled(false);
                try {
                    String ip = Objects.requireNonNull(ipText.getText()).toString();
                    int port = Integer.parseInt(Objects.requireNonNull(portText.getText()).toString());
                    String clientName = Objects.requireNonNull(clientNameText.getText()).toString();
                    boolean useTLS = enable_ssl_checkbox.isChecked();

                    if (ip.isEmpty() || clientName.isEmpty()) {
                        showErrorDialog("Input Error", "IP address and Client Name cannot be empty.");
                        state.set(DISCONNECTED);
                        updateConnectionStatusOnUiThread();
                        return;
                    }

                    InetSocketAddress addressPort = new InetSocketAddress(ip, port);
                    updatePreferences(clientName, ip, port, useTLS);
                    state.set(CONNECTING);
                    updateConnectionStatusOnUiThread();

                    SocketFactoryInterface socketFactory;
                    if (useTLS) socketFactory = new TLSSocketFactory();
                    else socketFactory = new TCPSocketFactory();

                    Client client = createClient(addressPort, clientName);
                    final Exception[] connectException = {null};
                    executorService.execute(() -> {
                        try {
                            client.connect(MainActivity.this, socketFactory);
                            state.set(CONNECTED);
                        } catch (Exception e) {
                            Log.error("TCP Connection failed: " + e.getMessage());
                            connectException[0] = e;
                            state.set(DISCONNECTED);
                        }
                        updateConnectionStatusOnUiThread();
                        if (state.get() == DISCONNECTED && connectException[0] != null) {
                            runOnUiThread(() -> showErrorDialog("TCP Connection Failed", connectException[0].getMessage() != null ? connectException[0].getMessage() : "Unknown error"));
                        }
                    });
                } catch (NumberFormatException e) {
                    Log.error("Port parsing error: " + e.getMessage());
                    showErrorDialog("Configuration Error", "Invalid port number.");
                    state.set(DISCONNECTED);
                    updateConnectionStatusOnUiThread();
                } catch (NullPointerException e) {
                    Log.error("Text field empty: " + e.getMessage());
                    showErrorDialog("Configuration Error", "IP, Port, or Client Name is empty.");
                    state.set(DISCONNECTED);
                    updateConnectionStatusOnUiThread();
                } catch (Exception e) {
                    Log.error("Error setting up TCP connection: " + e.getMessage());
                    showErrorDialog("TCP Configuration Error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                    state.set(DISCONNECTED);
                    updateConnectionStatusOnUiThread();
                }
            } else if (bluetooth_RadioButton.isChecked()) {
                if (bluetoothAdapter == null) {
                    showErrorDialog(getString(R.string.bluetooth_error_title), getString(R.string.bluetooth_not_supported_message));
                    state.set(DISCONNECTED);
                    updateConnectionStatusOnUiThread();
                    return;
                }

                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        enableBluetoothLauncher.launch(enableBtIntent);
                    } else {
                        Log.debug("BLUETOOTH_CONNECT permission needed to request enabling Bluetooth.");
                        proceedWithBluetoothConnection();
                    }
                    return;
                }
                proceedWithBluetoothConnection();
            }
        }
    }

    private void proceedWithBluetoothConnection() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            if (bluetoothAdapter == null) {
                showErrorDialog(getString(R.string.bluetooth_error_title), getString(R.string.bluetooth_not_supported_message));
            } else {
                showErrorDialog(getString(R.string.bluetooth_required_title), getString(R.string.bluetooth_required_message_enable_first));
            }
            state.set(DISCONNECTED);
            updateConnectionStatusOnUiThread();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.debug("Requesting Bluetooth SCAN and CONNECT permissions.");
                requestBluetoothPermissionsLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
                return;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.debug("ACCESS_FINE_LOCATION permission might be needed for Bluetooth discovery on this Android version but is not granted.");
                }
            }
        }
        startBluetoothDiscovery();
    }

    @SuppressLint("MissingPermission")
    private void startBluetoothDiscovery() {
        Log.debug("Starting Bluetooth discovery...");
        state.set(CONNECTING);
        runOnUiThread(() -> {
            startClientBtn.setText(getString(R.string.scanning_bluetooth_devices));
            startClientBtn.setEnabled(false);
            connection_type_RadioGroup.setEnabled(false);
        });

        discoveredDevicesList.clear();
        ArrayList<String> deviceNamesForAdapter = new ArrayList<>();
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice, deviceNamesForAdapter);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(discoveryReceiver, filter);
            }
        } catch (Exception e) {
            Log.error("Error registering discovery receiver: " + e.getMessage());
            showErrorDialog(getString(R.string.bluetooth_error_title), getString(R.string.bluetooth_error_cannot_listen));
            state.set(DISCONNECTED);
            updateConnectionStatusOnUiThread();
            return;
        }

        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            boolean discoveryStarted = bluetoothAdapter.startDiscovery();
            if (!discoveryStarted) {
                Log.error("Failed to start Bluetooth discovery via adapter.startDiscovery().");
                showErrorDialog(getString(R.string.bluetooth_error_title), getString(R.string.bluetooth_error_discovery_failed));
                state.set(DISCONNECTED);
                updateConnectionStatusOnUiThread();
                try {
                    unregisterReceiver(discoveryReceiver);
                } catch (IllegalArgumentException ignored) {
                }
            } else {
                Log.debug("Bluetooth discovery initiated successfully.");
            }
        } catch (SecurityException e) {
            Log.error("SecurityException during Bluetooth discovery initiation: " + e.getMessage());
            showErrorDialog(getString(R.string.bluetooth_permission_error_title), getString(R.string.bluetooth_permission_error_discovery) + e.getMessage());
            state.set(DISCONNECTED);
            updateConnectionStatusOnUiThread();
            try {
                unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    try {
                        String deviceName = device.getName();
                        String deviceAddress = device.getAddress();
                        if (deviceName != null && !deviceInList(device)) {
                            discoveredDevicesList.add(device);
                            discoveredDevicesAdapter.add(deviceName + "\n" + deviceAddress);
                            discoveredDevicesAdapter.notifyDataSetChanged();
                            Log.debug("Found device: " + deviceName + " (" + deviceAddress + ")");
                        }
                    } catch (SecurityException e) {
                        Log.error("SecurityException getting device details during discovery: " + e.getMessage());
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.debug("Bluetooth discovery finished.");
                try {
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                } catch (SecurityException se) {
                    Log.error("SecurityException cancelling discovery post-finish: " + se.getMessage());
                }
                try {
                    unregisterReceiver(this);
                } catch (IllegalArgumentException e) {
                    Log.debug("Discovery receiver already unregistered or never registered.");
                }

                if (discoveredDevicesList.isEmpty()) {
                    showErrorDialog(getString(R.string.bluetooth_discovery_title), getString(R.string.bluetooth_no_devices_found));
                    state.set(DISCONNECTED);
                } else {
                    showDiscoveredDevicesDialog();
                }
                updateConnectionStatusOnUiThread();
            }
        }
    };

    @SuppressLint("MissingPermission")
    private boolean deviceInList(BluetoothDevice device) {
        String newDeviceAddress = device.getAddress();
        if (newDeviceAddress == null) return false;

        for (BluetoothDevice d : discoveredDevicesList) {
            if (newDeviceAddress.equals(d.getAddress())) {
                return true;
            }
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    private void showDiscoveredDevicesDialog() {
        if (discoveredDevicesAdapter == null || discoveredDevicesAdapter.getCount() == 0) {
            Log.debug("No devices found or adapter is null/empty when trying to show dialog.");
            state.set(DISCONNECTED);
            updateConnectionStatusOnUiThread();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_bluetooth_device_title));
        builder.setAdapter(discoveredDevicesAdapter, (dialog, which) -> {
            try {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } catch (SecurityException se) {
                Log.error("SecurityException cancelling discovery on device selection: " + se.getMessage());
            }

            BluetoothDevice selectedDevice = discoveredDevicesList.get(which);
            String deviceName = selectedDevice.getName();
            String deviceAddress = selectedDevice.getAddress();
            Log.debug("Selected device: " + (deviceName != null ? deviceName : "Unknown Name") + " | " + deviceAddress);
            connectToDevice(selectedDevice);
            dialog.dismiss();
        });

        builder.setOnCancelListener(dialog -> {
            Log.debug("Device selection dialog cancelled.");
            try {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } catch (SecurityException se) {
                Log.error("SecurityException cancelling discovery on dialog cancel: " + se.getMessage());
            }
            state.set(DISCONNECTED);
            updateConnectionStatusOnUiThread();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        String deviceNameStr;
        try {
            deviceNameStr = device.getName() != null ? device.getName() : device.getAddress();
        } catch (SecurityException se) {
            Log.error("SecurityException getting device name in connectToDevice: " + se.getMessage());
            deviceNameStr = device.getAddress();
        }

        Log.info("Attempting to connect to " + deviceNameStr);
        final String finalDeviceName = deviceNameStr;

        state.set(CONNECTING);
        isBluetoothConnected = false;
        runOnUiThread(() -> {
            startClientBtn.setText(getString(R.string.connecting_to_device, finalDeviceName));
            startClientBtn.setEnabled(false);
            connection_type_RadioGroup.setEnabled(false);
        });

        executorService.execute(() -> {
            BluetoothSocket tempSocket = null;
            try {
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                Log.debug("Bluetooth RFCOMM socket created. Attempting to connect...");
                tempSocket.connect();

                mConnectedBluetoothSocket = tempSocket;
                isBluetoothConnected = true;
                Log.info("Successfully connected to Bluetooth device: " + finalDeviceName);
                state.set(CONNECTED);

                runOnUiThread(() -> {
                    showErrorDialog(getString(R.string.bluetooth_connected_title), getString(R.string.bluetooth_connected_message, finalDeviceName));
                    updateConnectionStatusOnUiThread();
                });

            } catch (SecurityException se) {
                Log.error("Bluetooth connection failed (SecurityException) to " + finalDeviceName + ": " + se.getMessage());
                final String message = se.getMessage();
                runOnUiThread(() -> showErrorDialog(getString(R.string.bluetooth_connection_failed_title), getString(R.string.permission_error_message, message)));
                closeSocketAndResetState(tempSocket);
            } catch (IOException e) {
                Log.error("Bluetooth connection failed (IOException) to " + finalDeviceName + ": " + e.getMessage());
                final String message = e.getMessage();
                runOnUiThread(() -> showErrorDialog(getString(R.string.bluetooth_connection_failed_title), getString(R.string.could_not_connect_message, message)));
                closeSocketAndResetState(tempSocket);
            }
        });
    }

    private void closeSocketAndResetState(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.error("Failed to close Bluetooth socket: " + e.getMessage());
            }
        }
        if (mConnectedBluetoothSocket == socket) {
            mConnectedBluetoothSocket = null;
        }
        isBluetoothConnected = false;
        state.set(DISCONNECTED);
        updateConnectionStatusOnUiThread();
    }

    private void updateConnectionStatusOnUiThread() {
        runOnUiThread(() -> {
            try {
                updateConnectionStatus();
            } catch (IOException e) {
                Log.error("Error updating connection status UI: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            Log.debug("Discovery receiver not registered or already unregistered at onDestroy.");
        }

        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    bluetoothAdapter.cancelDiscovery();
                } else {
                    Log.debug("Cannot cancel Bluetooth discovery in onDestroy due to missing BLUETOOTH_SCAN permission.");
                }
            }
        } catch (SecurityException se) {
            Log.error("SecurityException cancelling discovery in onDestroy: " + se.getMessage());
        }

        if (mConnectedBluetoothSocket != null) {
            try {
                mConnectedBluetoothSocket.close();
                Log.info("Bluetooth socket closed in onDestroy.");
            } catch (IOException e) {
                Log.error("Error closing Bluetooth socket in onDestroy: " + e.getMessage());
            }
            mConnectedBluetoothSocket = null;
        }
        executorService.shutdownNow();
    }

    private void showErrorDialog(String title, String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show());
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    @SuppressLint("ServiceCast")
    private void dispatchGesture(@NonNull GestureDescription gesture,
                                 AccessibilityService.GestureResultCallback gestureResultCallback,
                                 Object o) {
        Log.debug("dispatchGesture called in MainActivity - this usually happens from within an AccessibilityService.");
    }
}

