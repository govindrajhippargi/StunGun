package com.example.mark1;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private TextView tvStatus;
    private EditText etPhoneNumbers, etMessage;
    private String savedPhoneNumbers = "", savedMessage = "";

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS_NAME = "Mark1Prefs";
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        tvStatus = findViewById(R.id.tv_status);
        etPhoneNumbers = findViewById(R.id.et_phone_numbers);
        etMessage = findViewById(R.id.et_message);

        Button btnScan = findViewById(R.id.btn_scan);
        Button btnSavePhone = findViewById(R.id.btn_save_phone);
        Button btnSaveMessage = findViewById(R.id.btn_save_message);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Request Bluetooth permissions for Android S+
        checkPermissions();

        // Load saved preferences
        loadPreferences();

        // Set button click listeners
        btnScan.setOnClickListener(v -> scanBluetoothDevices());
        btnSavePhone.setOnClickListener(v -> savePhoneNumbers());
        btnSaveMessage.setOnClickListener(v -> saveMessage());

        // Start listening for Bluetooth data
        listenForBluetoothData();
    }

    private void scanBluetoothDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceList = new ArrayList<>();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Paired Devices")
                .setItems(deviceList.toArray(new String[0]), (dialog, which) -> {
                    // Handle device selection
                    connectToBluetooth(pairedDevices.toArray(new BluetoothDevice[0])[which]);
                });
        builder.show();
    }

    private void connectToBluetooth(BluetoothDevice device) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            tvStatus.setText("Status: Connected to " + device.getName());
            Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            tvStatus.setText("Status: Connection Failed");
            Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePhoneNumbers() {
        savedPhoneNumbers = etPhoneNumbers.getText().toString();
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putString("phone_numbers", savedPhoneNumbers).apply();
        Toast.makeText(this, "Phone Number Saved", Toast.LENGTH_SHORT).show();
    }

    private void saveMessage() {
        savedMessage = etMessage.getText().toString();
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putString("message", savedMessage).apply();
        Toast.makeText(this, "Message Saved", Toast.LENGTH_SHORT).show();
    }

    private void loadPreferences() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        savedPhoneNumbers = preferences.getString("phone_numbers", "");
        savedMessage = preferences.getString("message", "");
        etPhoneNumbers.setText(savedPhoneNumbers);
        etMessage.setText(savedMessage);
    }

    private void listenForBluetoothData() {
        new Thread(() -> {
            try {
                while (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
                    Thread.sleep(500);
                }
                inputStream = bluetoothSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytes;

                while ((bytes = inputStream.read(buffer)) != -1) {
                    String data = new String(buffer, 0, bytes);
                    Log.d("BluetoothData", "Received: " + data);
                    if (data.trim().equals("SEND_SMS")) {
                        sendSmsAutomatically();
                    }
                }
            } catch (Exception e) {
                Log.e("BluetoothData", "Error reading Bluetooth data: " + e.getMessage());
            }
        }).start();
    }

    private void sendSmsAutomatically() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            SmsManager smsManager = SmsManager.getDefault();

            // Split the saved phone numbers by comma
            String[] phoneNumbers = savedPhoneNumbers.split(",");

            // Send SMS to each phone number
            for (String phoneNumber : phoneNumbers) {
                if (phoneNumber.trim().isEmpty()) continue;

                // Create PendingIntents to track SMS delivery
                Intent sentIntent = new Intent("SMS_SENT");
                PendingIntent sentPendingIntent = PendingIntent.getBroadcast(this, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE);

                // Send the SMS
                smsManager.sendTextMessage(phoneNumber.trim(), null, savedMessage, sentPendingIntent, null);

                // Register a BroadcastReceiver to handle the result
                registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        switch (getResultCode()) {
                            case Activity.RESULT_OK:
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SMS Sent to " + phoneNumber, Toast.LENGTH_SHORT).show());
                                break;
                            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SMS Failed: Generic Failure", Toast.LENGTH_SHORT).show());
                                break;
                            case SmsManager.RESULT_ERROR_NO_SERVICE:
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SMS Failed: No Service", Toast.LENGTH_SHORT).show());
                                break;
                            case SmsManager.RESULT_ERROR_NULL_PDU:
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SMS Failed: Null PDU", Toast.LENGTH_SHORT).show());
                                break;
                            case SmsManager.RESULT_ERROR_RADIO_OFF:
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SMS Failed: Radio Off", Toast.LENGTH_SHORT).show());
                                break;
                        }
                    }
                }, new IntentFilter("SMS_SENT"));
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, PERMISSION_REQUEST_CODE);
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.SEND_SMS
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}