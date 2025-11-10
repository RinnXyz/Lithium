// IWriteCallback.aidl
package com.rinn.engine;

interface IWriteCallback {
    void onComplete();
    void onError(int code, String message);
}
