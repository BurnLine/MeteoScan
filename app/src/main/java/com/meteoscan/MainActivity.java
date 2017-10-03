package com.meteoscan;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 123;
    private static final int REQUEST_ENABLE_BT = 356;

    private BluetoothAdapter bluetoothAdapter;
    private ProgressBar progressBar;
    private TextView batteryValue;

    private TextView temperature;
    private TextView humidity;
    private TextView dewPoint;
    private TextView pressure;
    private TextView elevation;
    private TextView lastUpdate;
    private TextView lastPacket;
    private TextView rssi;
    private TextView packetCount;
    private TextView id;

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            parseData(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.e("BT", "LIST " + results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e("BT", "Error: " + errorCode);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        batteryValue = (TextView)findViewById(R.id.batteryValue);

        temperature = (TextView)findViewById(R.id.temperature);
        humidity = (TextView)findViewById(R.id.humidity);
        dewPoint = (TextView)findViewById(R.id.dewPoint);
        pressure = (TextView)findViewById(R.id.pressure);
        elevation = (TextView)findViewById(R.id.elevation);
        lastUpdate = (TextView)findViewById(R.id.update);
        lastPacket = (TextView)findViewById(R.id.lastpacket);
        rssi = (TextView)findViewById(R.id.rssi);
        packetCount = (TextView)findViewById(R.id.packetcount);
        id = (TextView)findViewById(R.id.id);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.icon_small);
        } else if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeAsUpIndicator(R.drawable.icon_small);
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!bluetoothAdapter.isEnabled()) {
            //bluetoothAdapter.enable();
            Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intentBtEnabled, REQUEST_ENABLE_BT);
        } else {
            if (!fuckMarshMallow()) {
                startScan();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
        Toast.makeText(this, "Scan stopped!", Toast.LENGTH_SHORT).show();
    }

    public void startScan() {
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                /*.setDeviceAddress("F6:7C:15:AC:84:74")*/
                /*.setManufacturerData(0x9101, new byte[]{})*/.build();
        ScanSettings settings = new ScanSettings.Builder()
                /*.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)*/
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        filters.add(filter);
        BluetoothLeScanner leScanner = bluetoothAdapter.getBluetoothLeScanner();
        leScanner.flushPendingScanResults(scanCallback);
        leScanner.startScan(filters, settings, scanCallback);
        Toast.makeText(this, "Scan started!", Toast.LENGTH_SHORT).show();
    }

    public void parseData(ScanResult scanResult) {
        ScanRecord scanRecord = scanResult.getScanRecord();

        if (scanRecord == null) {
            return;
        }

        byte[] data = scanRecord.getBytes();
        //Log.w("BT", bytesToHex(data));
        Date date = Calendar.getInstance().getTime();
        String formattedDate = simpleDateFormat.format(date);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);


        double temp;
        double humidity;
        double devPoint;
        double pressure;
        short elevation;
        short packets;
        byte battery;
        byte[] idecko = new byte[5];

        int id = buffer.getInt();

        lastPacket.setText("Posledné dáta: " + formattedDate);

        if (id != 0x03001903) {
            bluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(scanCallback);
            return;
        }

        buffer.position(buffer.position() + 7);
        temp = buffer.getShort() / 10.0;
        humidity = buffer.getShort() / 10.0;
        devPoint = buffer.getShort() / 10.0;
        pressure = buffer.getInt() / 10.0;
        elevation = buffer.getShort();
        battery = buffer.get();
        packets = buffer.getShort();

        idecko[0] = buffer.get();
        idecko[1] = buffer.get();
        idecko[2] = buffer.get();
        idecko[3] = buffer.get();
        idecko[4] = buffer.get();

        Log.i("BT", String.format("T=%f Hum=%f DevP=%f Pres=%f Elev=%d Bat=%d%%", temp, humidity, devPoint, pressure, elevation, battery));

        progressBar.setProgress(battery);
        batteryValue.setText(String.format(Locale.ENGLISH, "Baterka: %d%%", battery));

        temperature.setText(String.format(Locale.ENGLISH, "%.1f °C", temp));
        this.humidity.setText(String.format(Locale.ENGLISH, "%.1f %%", humidity));
        dewPoint.setText(String.format(Locale.ENGLISH, "%.1f °C", devPoint));
        this.pressure.setText(String.format(Locale.ENGLISH, "%.1f hPa", pressure));
        this.elevation.setText(String.format(Locale.ENGLISH, "%d m", elevation));
        rssi.setText(String.format(Locale.ENGLISH, "%d dBm", scanResult.getRssi()));
        packetCount.setText(String.format(Locale.ENGLISH, "Počítadlo: %d", packets));
        this.id.setText(String.format(Locale.ENGLISH, "ID: 0x%s", bytesToHex(idecko)));
        lastUpdate.setText("Posledná aktualizácia: " + formattedDate);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == 0) {
                    Toast.makeText(MainActivity.this, "Bluetooth was not enabled!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!fuckMarshMallow()) {
                    startScan();
                }
                break;
            default:
                System.out.print(requestCode + " " + resultCode + " " + data.toString());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);


                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                // Check for ACCESS_FINE_LOCATION
                if (perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

                        ) {
                    // All Permissions Granted

                    // Permission Denied
                    Toast.makeText(MainActivity.this, "All Permission GRANTED !! Thank You :)", Toast.LENGTH_SHORT)
                            .show();


                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "One or More Permissions are DENIED Exiting App :(", Toast.LENGTH_SHORT)
                            .show();

                    finish();
                }

                if (!bluetoothAdapter.isEnabled()) {
                    //bluetoothAdapter.enable();
                    Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intentBtEnabled, REQUEST_ENABLE_BT);
                } else {
                    if (!fuckMarshMallow()) {
                        startScan();
                    }
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private boolean fuckMarshMallow() {
        List<String> permissionsNeeded = new ArrayList<>();

        final List<String> permissionsList = new ArrayList<>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location");

        if (permissionsList.size() > 0) {
            if (permissionsNeeded.size() > 0) {

                // Need Rationale
                String message = "App need access to " + permissionsNeeded.get(0);

                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                showMessageOKCancel(message, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                return true;
            }
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return true;
        }

        return false;
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }

}
