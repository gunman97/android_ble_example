package org.airpage.bluetooth.example;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import static org.airpage.bluetooth.example.BluetoothUtils.REQUEST_ENABLE_BT;
import static org.airpage.bluetooth.example.BluetoothUtils.REQUEST_FINE_LOCATION;
import static org.airpage.bluetooth.example.BluetoothUtils.SCAN_PERIOD;
import static org.airpage.bluetooth.example.BluetoothUtils.UUID_DKDK_SERVICE;


public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.toString();

    private BluetoothLeScanner bleScanner_;
    private BluetoothGatt bleGatt_;
    private BluetoothAdapter bleAdapter_;

    private Handler scanHandler_;
    private ArrayList<BluetoothDevice> deviceList_;
    private ScanCallback scanCb_;

    private boolean bIsConnected_ = false;
    private boolean bIsScanning_ = false;
    private boolean bIsMotorOn_ = false;

    private Button connectBtn;
    private Button sendBtn;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //블루투스 지원이 안되면 걍 종료
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        BluetoothManager bleManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter_ = bleManager.getAdapter();

        UIinit();
    }

    private void UIinit() {
        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        connectBtn = findViewById(R.id.connectBtn);
        connectBtn.setText("Start scan");
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bIsScanning_ == true) {
                    stopScan();
                }
                else if (bIsConnected_ == true){
                    disconnectGattServer();
                }
                else if (bIsScanning_ == false) {
                    startScan();
                }
            }
        });

        sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendData();
            }
        });
        sendBtn.setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if( !getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) ) {
            finish();
            return;
        }

    }

    @Override
    public void onPause() {
        super.onPause();

        disconnectGattServer();

    }


    //6바이트짜리 데이터 보내기
    private void sendData() {
        if( !bIsConnected_)
        {
            Log.e( TAG, "Failed to sendData" );
            return;
        }

        BluetoothGattCharacteristic _cmdCharacteristic= BluetoothUtils.findCommandCharacteristic( bleGatt_ );

        if( _cmdCharacteristic == null ) {
            disconnectGattServer();
            return;
        }

        byte[] cmds= new byte[6];
        cmds[0]= 0;
        cmds[1]= 0;
        cmds[2]= 0;
        cmds[3]= 0;
        cmds[4]= 0;
        cmds[5]= 0;

        if (bIsMotorOn_ == true) {
            cmds[0]= 1; // 모터 끄기
            setCmdBtn("Motor On", true);
        }
        else {
            setCmdBtn("Motor Off", true);
        }

        _cmdCharacteristic.setValue( cmds );

        if( bleGatt_.writeCharacteristic( _cmdCharacteristic ) ) {
            Log.d( TAG, "Successfully sent data." );
            bIsMotorOn_ = !bIsMotorOn_;
        }
        else
        {
            Log.e( TAG, "Failed to send data" );
            bIsMotorOn_ = false;
            disconnectGattServer();
        }
    }

    private void startScan() {
        if (bleAdapter_ == null || !bleAdapter_.isEnabled()) {
            enableBLE();
            return;
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermission();
                return;
            }
        }

        List<ScanFilter> filters= new ArrayList<>();
        ScanFilter scan_filter= new ScanFilter.Builder()
                .setServiceUuid( new ParcelUuid( UUID_DKDK_SERVICE ) )
                .build();
        filters.add( scan_filter );

        ScanSettings settings= new ScanSettings.Builder()
                .setScanMode( ScanSettings.SCAN_MODE_LOW_POWER )
                .build();

        deviceList_ = new ArrayList<>();
        scanCb_ = new BLEScanCallback(deviceList_);
        bleScanner_ = bleAdapter_.getBluetoothLeScanner();
        bleScanner_.startScan( filters, settings, scanCb_);

        setConnectBtn("Stop scanning", true);

        scanHandler_ = new Handler();
        scanHandler_.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, SCAN_PERIOD );

        bIsScanning_ = true;
    }

    private void stopScan() {
        if( bIsScanning_ && bleAdapter_ != null && bleAdapter_.isEnabled() && bleScanner_ != null ) {
            bleScanner_.stopScan(scanCb_);
        }

        bIsScanning_ = false;
        scanCb_ = null;
        scanHandler_ = null;

        Log.d(TAG, "Scan is stopped.");

        if(bIsConnected_ == false)
            setConnectBtn("Start scanning", true);
    }


    private void scanFinished() {
        if( deviceList_.isEmpty() ) {
            setConnectBtn("Start scanning", true);
            return;
        }


        for( BluetoothDevice _device : deviceList_) {

            ParcelUuid[] uuids = _device.getUuids();

            if (uuids != null)
                for (ParcelUuid uuid : uuids) {
                    Log.d( TAG, "device uuid: " + uuid.toString() );
                }

            if (_device.getAddress() != null)
                Log.d( TAG, "device address: " + _device.getAddress() );

            if (_device.getName() != null)
                Log.d( TAG, "device address: " + _device.getName() );

            connectDevice(_device);
            return;

        }
    }

    private void connectDevice( BluetoothDevice _device ) {
        GattClientCallback gattClientCb = new GattClientCallback();
        Log.d( TAG, "Try to connect " + _device.getName() );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bleGatt_= _device.connectGatt( this, false, gattClientCb, BluetoothDevice.TRANSPORT_LE);
        }
        else {
            bleGatt_= _device.connectGatt( this, false, gattClientCb);
        }

    }

    private void setConnectBtn(final String desc, final boolean bEnable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectBtn.setText(desc);
                connectBtn.setEnabled(bEnable);
            }
        });
    }

    private void setCmdBtn(final String desc, final boolean bEnable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendBtn.setText(desc);
                sendBtn.setEnabled(bEnable);
            }
        });
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange( BluetoothGatt _gatt, int _status, int _new_state ) {
            super.onConnectionStateChange( _gatt, _status, _new_state );
            if( _new_state == BluetoothProfile.STATE_CONNECTED ) {
                bIsConnected_ = true;
                Log.d( TAG, "Connected to the GATT server" );
                _gatt.discoverServices();
            } else if ( _new_state == BluetoothProfile.STATE_DISCONNECTED ) {
                Log.d( TAG, "status is STATE_DISCONNECTED" );
                disconnectGattServer();

                setConnectBtn("Start scanning", true);
                setCmdBtn("Send cmd", false);
            }
        }

        @Override
        public void onServicesDiscovered( BluetoothGatt _gatt, int _status ) {
            super.onServicesDiscovered( _gatt, _status );

            if( _status != BluetoothGatt.GATT_SUCCESS ) {
                Log.e( TAG, "Discovery failed, status: " + _status );
                setConnectBtn("Start scanning", true);
                setCmdBtn("Send cmd", true);
                return;
            }

            List<BluetoothGattCharacteristic> matching_characteristics = BluetoothUtils.findBLECharacteristics( _gatt );
            if( matching_characteristics.isEmpty() ) {
                Log.e( TAG, "failed to find characteristic" );
                setConnectBtn("Start scanning", true);
                setCmdBtn("Send cmd", true);
                return;
            }

            Log.d( TAG, "Services discovery : success" );

            setConnectBtn("Disconnect", true);
            setCmdBtn("Send cmd", true);
        }

        @Override
        public void onCharacteristicChanged( BluetoothGatt _gatt, BluetoothGattCharacteristic _characteristic ) {
            super.onCharacteristicChanged( _gatt, _characteristic );

        }

        @Override
        public void onCharacteristicWrite( BluetoothGatt _gatt, BluetoothGattCharacteristic _characteristic, int _status ) {
            super.onCharacteristicWrite( _gatt, _characteristic, _status );
            if( _status == BluetoothGatt.GATT_SUCCESS ) {
                Log.d( TAG, "onCharacteristicWrite : SUCCESS" );
            } else {
                Log.d( TAG, "onCharacteristicWrite : FAILED" );
                //disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt _gatt, BluetoothGattCharacteristic _characteristic, int _status) {
            super.onCharacteristicRead(_gatt, _characteristic, _status);
            if (_status == BluetoothGatt.GATT_SUCCESS) {
                Log.d( TAG, "onCharacteristicRead : SUCCESS" );
                //readCharacteristic(characteristic);
            } else {
                Log.e( TAG, "Characteristic read unsuccessful, status: " + _status);

            }
        }

        private void readCharacteristic( BluetoothGattCharacteristic _characteristic ) {
            byte[] msg= _characteristic.getValue();
            Log.d( TAG, "read: " + msg.toString() );
        }
    }

    public void disconnectGattServer() {

        bIsConnected_ = false;

        if( bleGatt_ != null ) {
            bleGatt_.disconnect();
            bleGatt_.close();
        }

        setConnectBtn("Start scanning", true);
        setCmdBtn("Send cmd", false);
    }

    private class BLEScanCallback extends ScanCallback {
        private ArrayList<BluetoothDevice> _foundDevices;

        BLEScanCallback( ArrayList<BluetoothDevice>  _scanDeviceList ) {
            _foundDevices = _scanDeviceList;
        }

        @Override
        public void onScanResult( int _callback_type, ScanResult _result ) {
            addScanResult( _result );

            new Handler().postDelayed( new Runnable() {
                @Override
                public void run() {
                    scanFinished();
                }
            }, 100 );
        }

        @Override
        public void onBatchScanResults( List<ScanResult> _results ) {
            for( ScanResult result: _results ) {
                addScanResult( result );
            }

            new Handler().postDelayed( new Runnable() {
                @Override
                public void run() {
                    scanFinished();
                }
            }, 100 );
        }

        @Override
        public void onScanFailed( int _error ) {
            Log.e( TAG, "Scan failed : " +_error );
        }

        private void addScanResult( ScanResult _result ) {
            BluetoothDevice device= _result.getDevice();
            _foundDevices.add(device );
        }
    }

    private void enableBLE() {
        Intent ble_enable_intent= new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( ble_enable_intent, REQUEST_ENABLE_BT );
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }
    }
}
