package com.polidea.rxandroidble.internal;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.util.Log;

import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.exceptions.BleGattException;
import com.polidea.rxandroidble.exceptions.BleGattOperationType;

import java.util.UUID;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class RxBleGattCallback {
    
    private final String TAG = "RxBleGattCallback(" + System.identityHashCode(this) + ')';

    private Scheduler callbackScheduler = Schedulers.newThread();

    private BehaviorSubject<Void> statusErrorSubject = BehaviorSubject.create();

    private BehaviorSubject<RxBleConnection.RxBleConnectionState> connectionStateBehaviorSubject = BehaviorSubject.create(
            RxBleConnection.RxBleConnectionState.DISCONNECTED);

    private PublishSubject<RxBleDeviceServices> servicesDiscoveredPublishSubject = PublishSubject.create();
    // TODO: [PU] 29.01.2016 Why BehaviorSubject?
    private BehaviorSubject<Pair<UUID, byte[]>> readCharacteristicBehaviorSubject = BehaviorSubject.create();

    private PublishSubject<Pair<UUID, byte[]>> writeCharacteristicPublishSubject = PublishSubject.create();

    private PublishSubject<Pair<UUID, byte[]>> changedCharacteristicPublishSubject = PublishSubject.create();

    private PublishSubject<Pair<UUID, byte[]>> reliableWriteCharacteristicPublishSubject = PublishSubject.create();

    private PublishSubject<Pair<BluetoothGattDescriptor, byte[]>> readDescriptorPublishSubject = PublishSubject.create();

    private PublishSubject<Pair<BluetoothGattDescriptor, byte[]>> writeDescriptorPublishSubject = PublishSubject.create();

    private PublishSubject<Integer> readRssiPublishSubject = PublishSubject.create();

    private PublishSubject<Integer> changedMtuPublishSubject = PublishSubject.create();

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, String.format("onConnectionStateChange newState=%d status=%d", newState, status));
            super.onConnectionStateChange(gatt, status, newState);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.CONNECTION_STATE)) {
                return;
            }

            Observable.just(mapConnectionStateToRxBleConnectionStatus(newState))
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(connectionStateBehaviorSubject::onNext);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, String.format("onServicesDiscovered status=%d", status));
            super.onServicesDiscovered(gatt, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.SERVICE_DISCOVERY)) {
                return;
            }

            Observable.just(gatt)
                    .map(BluetoothGatt::getServices)
                    .map(RxBleDeviceServices::new)
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(servicesDiscoveredPublishSubject::onNext);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // TODO: [PU] 29.01.2016 String formatting will cause huge delays in big-throughput.
            Log.d(TAG, String.format("onCharacteristicRead characteristic=%s status=%d", characteristic.getUuid(), status));
            super.onCharacteristicRead(gatt, characteristic, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.CHARACTERISTIC_READ)) {
                return;
            }

            Observable.just(characteristic)
                    .map(mapToUUIDAndValuePair())
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(readCharacteristicBehaviorSubject::onNext);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, String.format("onCharacteristicWrite characteristic=%s status=%d", characteristic.getUuid(), status));
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.CHARACTERISTIC_WRITE)) {
                return;
            }

            Observable.just(characteristic)
                    .map(mapToUUIDAndValuePair())
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(writeCharacteristicPublishSubject::onNext);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, String.format("onCharacteristicChanged characteristic=%s", characteristic.getUuid()));
            super.onCharacteristicChanged(gatt, characteristic);

            Observable.just(characteristic)
                    .map(mapToUUIDAndValuePair())
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(changedCharacteristicPublishSubject::onNext);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, String.format("onCharacteristicRead descriptor=%s status=%d", descriptor.getUuid(), status));
            super.onDescriptorRead(gatt, descriptor, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.DESCRIPTOR_READ)) {
                return;
            }

            Observable.just(descriptor)
                    .map(gattDescriptor -> new Pair<>(descriptor, gattDescriptor.getValue()))
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(readDescriptorPublishSubject::onNext);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, String.format("onDescriptorWrite descriptor=%s status=%d", descriptor.getUuid(), status));
            super.onDescriptorWrite(gatt, descriptor, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.DESCRIPTOR_WRITE)) {
                return;
            }

            Observable.just(descriptor)
                    .map(gattDescriptor -> new Pair<>(gattDescriptor, gattDescriptor.getValue()))
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(writeDescriptorPublishSubject::onNext);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.d(TAG, String.format("onReliableWriteCompleted status=%d", status));
            super.onReliableWriteCompleted(gatt, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.RELIABLE_WRITE_COMPLETED)) {
                return;
            }

            // TODO
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, String.format("onReadRemoteRssi rssi=%d status=%d", rssi, status));
            super.onReadRemoteRssi(gatt, rssi, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.READ_RSSI)) {
                return;
            }

            Observable.just(rssi)
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(readRssiPublishSubject::onNext);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(TAG, String.format("onMtuChanged mtu=%d status=%d", mtu, status));
            super.onMtuChanged(gatt, mtu, status);

            if (propagateStatusErrorIfGattErrorOccurred(status, BleGattOperationType.ON_MTU_CHANGED)) {
                return;
            }

            Observable.just(mtu)
                    .compose(getSubscribeAndObserveOnTransformer())
                    .subscribe(changedMtuPublishSubject::onNext);
        }
    };

    @NonNull
    private Func1<BluetoothGattCharacteristic, Pair<UUID, byte[]>> mapToUUIDAndValuePair() {
        return bluetoothGattCharacteristic -> new Pair<>(bluetoothGattCharacteristic.getUuid(), bluetoothGattCharacteristic.getValue());
    }

    private RxBleConnection.RxBleConnectionState mapConnectionStateToRxBleConnectionStatus(int newState) {

        switch (newState) {
            case BluetoothGatt.STATE_CONNECTING:
                return RxBleConnection.RxBleConnectionState.CONNECTING;
            case BluetoothGatt.STATE_CONNECTED:
                return RxBleConnection.RxBleConnectionState.CONNECTED;
            case BluetoothGatt.STATE_DISCONNECTING:
                return RxBleConnection.RxBleConnectionState.DISCONNECTING;
            default:
                return RxBleConnection.RxBleConnectionState.DISCONNECTED;
        }
    }

    private <T> Observable.Transformer<T, T> getSubscribeAndObserveOnTransformer() {
        return observable -> observable.subscribeOn(callbackScheduler).observeOn(callbackScheduler);
    }

    private boolean propagateStatusErrorIfGattErrorOccurred(int status, BleGattOperationType operationType) {
        final boolean isError = status != BluetoothGatt.GATT_SUCCESS;

        if (isError) {
            statusErrorSubject.onError(new BleGattException(status, operationType));
        }

        return isError;
    }

    private <T> Observable<T> withHandlingStatusError(Observable<T> observable) {
        //noinspection unchecked
        return Observable.merge(
                (Observable<? extends T>) statusErrorSubject.asObservable(), // statusErrorSubject emits only errors
                observable
        );
    }

    public BluetoothGattCallback getBluetoothGattCallback() {
        return bluetoothGattCallback;
    }

    public Observable<RxBleConnection.RxBleConnectionState> getOnConnectionStateChange() {
        return withHandlingStatusError(connectionStateBehaviorSubject);
    }

    public Observable<RxBleDeviceServices> getOnServicesDiscovered() {
        return withHandlingStatusError(servicesDiscoveredPublishSubject);
    }

    public Observable<Pair<UUID, byte[]>> getOnCharacteristicRead() {
        return withHandlingStatusError(readCharacteristicBehaviorSubject);
    }

    public Observable<Pair<UUID, byte[]>> getOnCharacteristicWrite() {
        return withHandlingStatusError(writeCharacteristicPublishSubject);
    }

    public Observable<Pair<UUID, byte[]>> getOnCharacteristicChanged() {
        return withHandlingStatusError(changedCharacteristicPublishSubject);
    }

    public Observable<Pair<BluetoothGattDescriptor, byte[]>> getOnDescriptorRead() {
        return withHandlingStatusError(readDescriptorPublishSubject);
    }

    public Observable<Pair<BluetoothGattDescriptor, byte[]>> getOnDescriptorWrite() {
        return withHandlingStatusError(writeDescriptorPublishSubject);
    }

    public Observable<Integer> getOnRssiRead() {
        return withHandlingStatusError(readRssiPublishSubject);
    }
}