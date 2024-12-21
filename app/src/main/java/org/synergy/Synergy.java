/*
 * synergy -- mouse and keyboard sharing utility
 * Copyright (C) 2010 Shaun Patterson
 * Copyright (C) 2010 The Synergy Project
 * Copyright (C) 2009 The Synergy+ Project
 * Copyright (C) 2002 Chris Schoeneman
 *
 * This package is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * found in the file COPYING that should have accompanied this file.
 *
 * This package is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.synergy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.sdsmdg.tastytoast.TastyToast;

import org.synergy.base.Event;
import org.synergy.base.EventQueue;
import org.synergy.base.EventType;
import org.synergy.base.utils.Log;
import org.synergy.client.Client;
import org.synergy.common.screens.BasicScreen;
import org.synergy.net.NetworkAddress;
import org.synergy.net.SocketFactoryInterface;
import org.synergy.net.SynergyConnectTask;
import org.synergy.net.TCPSocketFactory;

public class Synergy extends Activity {

    private final static String PROP_clientName = "clientName";
    private final static String PROP_serverHost = "serverHost";
    private final static String PROP_deviceName = "deviceName";

    private Thread mainLoopThread = null;


    private class MainLoopThread extends Thread {

        public void run() {
            try {
                Event event = new Event();
                event = EventQueue.getInstance().getEvent(event, -1.0);
                Log.note("Event grabbed");
                while (event.getType() != EventType.QUIT && mainLoopThread == Thread.currentThread()) {
                    EventQueue.getInstance().dispatchEvent(event);
                    // TODO event.deleteData ();
                    event = EventQueue.getInstance().getEvent(event, -1.0);
                    Log.note("Event grabbed");
                }
                mainLoopThread = null;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // TODO stop the accessibility injection service
            }
        }
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        String clientName = preferences.getString(PROP_clientName, null);
        if (clientName != null) {
            ((EditText) findViewById(R.id.clientNameEditText)).setText(clientName);
        }
        String serverHost = preferences.getString(PROP_serverHost, null);
        if (serverHost != null) {
            ((EditText) findViewById(R.id.serverHostEditText)).setText(serverHost);
        }

        // TODO make sure we have the appropriate permissions for the accessibility services. Otherwise display error/open settings intent

        final Button connectButton = (Button) findViewById(R.id.connectButton);
        // connect when clicked on the connectButton
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg) {
                connect();

            }
        });

        Log.setLogLevel(Log.Level.DEBUG);
        TastyToast.makeText(getApplicationContext(), "Client Starting", TastyToast.LENGTH_LONG, TastyToast.DEFAULT);
        Log.debug("Client starting....");
    }

    private void connect() {
        String clientName = ((EditText) findViewById(R.id.clientNameEditText)).getText().toString();
        String ipAddress = ((EditText) findViewById(R.id.serverHostEditText)).getText().toString();
        String portStr = ((EditText) findViewById(R.id.serverPortEditText)).getText().toString();
        int port = Integer.parseInt(portStr);
        String deviceName = ((EditText) findViewById(R.id.inputDeviceEditText)).getText().toString();

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();
        preferencesEditor.putString(PROP_clientName, clientName);
        preferencesEditor.putString(PROP_serverHost, ipAddress);
        preferencesEditor.putString(PROP_deviceName, deviceName);
        preferencesEditor.apply();

        try {
            SocketFactoryInterface socketFactory = new TCPSocketFactory();
            NetworkAddress serverAddress = new NetworkAddress(ipAddress, port);

            // TODO start the accessibility service injection here

            BasicScreen basicScreen = new BasicScreen();

            WindowManager wm = getWindowManager();

            Display display = wm.getDefaultDisplay();
            basicScreen.setShape(display.getWidth(), display.getHeight());
            Log.debug("Resolution: " + display.getWidth() + " x " + display.getHeight());


            //PlatformIndependentScreen screen = new PlatformIndependentScreen(basicScreen);
            Log.debug("Hostname: " + clientName);

            Client client = new Client(getApplicationContext(), clientName, serverAddress, socketFactory, null, basicScreen);
            new SynergyConnectTask().execute(client);
            TastyToast.makeText(getApplicationContext(), "Device Connected", TastyToast.LENGTH_LONG, TastyToast.SUCCESS);

            // TODO this looks quite hacky
            if (mainLoopThread == null) {
                mainLoopThread = new MainLoopThread();
                mainLoopThread.start();
            }

        } catch (Exception e) {
            TastyToast.makeText(getApplicationContext(), "Connection Failed", TastyToast.LENGTH_LONG, TastyToast.ERROR);
            e.printStackTrace();
        }
    }
}
