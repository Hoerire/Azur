package com.hoerire.tools.libterm.backend.shizuku;

/** 手写替代 aidl 生成物（本机无 aarch64 aidl 可执行文件）。 */
public interface ILibTermShizukuShellCallback extends android.os.IInterface {
    /** Default implementation for ILibTermShizukuShellCallback. */
    public static class Default implements com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback {
        @Override public void onOutput(long sessionId, int stream, byte[] data) throws android.os.RemoteException { }
        @Override public void onClosed(long sessionId, int exitCode) throws android.os.RemoteException { }
        @Override public void onError(long sessionId, java.lang.String message) throws android.os.RemoteException { }
        @Override public android.os.IBinder asBinder() { return null; }
    }

    public static abstract class Stub extends android.os.Binder implements com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback {
        private static final java.lang.String DESCRIPTOR = "com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback";

        public Stub() { this.attachInterface(this, DESCRIPTOR); }

        public static com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback asInterface(android.os.IBinder obj) {
            if ((obj == null)) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback))) {
                return ((com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback) iin);
            }
            return new com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback.Stub.Proxy(obj);
        }

        @Override public android.os.IBinder asBinder() { return this; }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            java.lang.String descriptor = DESCRIPTOR;
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(descriptor); return true; }
                case TRANSACTION_onOutput: {
                    data.enforceInterface(descriptor);
                    long _arg0 = data.readLong();
                    int _arg1 = data.readInt();
                    byte[] _arg2 = data.createByteArray();
                    this.onOutput(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onClosed: {
                    data.enforceInterface(descriptor);
                    long _arg0 = data.readLong();
                    int _arg1 = data.readInt();
                    this.onClosed(_arg0, _arg1);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onError: {
                    data.enforceInterface(descriptor);
                    long _arg0 = data.readLong();
                    java.lang.String _arg1 = data.readString();
                    this.onError(_arg0, _arg1);
                    reply.writeNoException();
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback {
            private android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            public java.lang.String getInterfaceDescriptor() { return DESCRIPTOR; }
            @Override public void onOutput(long sessionId, int stream, byte[] data) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(sessionId);
                    _data.writeInt(stream);
                    _data.writeByteArray(data);
                    boolean _status = mRemote.transact(Stub.TRANSACTION_onOutput, _data, _reply, 0);
                    if (!_status) { return; }
                    _reply.readException();
                } finally { _reply.recycle(); _data.recycle(); }
            }
            @Override public void onClosed(long sessionId, int exitCode) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(sessionId);
                    _data.writeInt(exitCode);
                    boolean _status = mRemote.transact(Stub.TRANSACTION_onClosed, _data, _reply, 0);
                    if (!_status) { return; }
                    _reply.readException();
                } finally { _reply.recycle(); _data.recycle(); }
            }
            @Override public void onError(long sessionId, java.lang.String message) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(sessionId);
                    _data.writeString(message);
                    boolean _status = mRemote.transact(Stub.TRANSACTION_onError, _data, _reply, 0);
                    if (!_status) { return; }
                    _reply.readException();
                } finally { _reply.recycle(); _data.recycle(); }
            }
        }
        static final int TRANSACTION_onOutput = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_onClosed = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_onError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    }

    public void onOutput(long sessionId, int stream, byte[] data) throws android.os.RemoteException;
    public void onClosed(long sessionId, int exitCode) throws android.os.RemoteException;
    public void onError(long sessionId, java.lang.String message) throws android.os.RemoteException;
}
