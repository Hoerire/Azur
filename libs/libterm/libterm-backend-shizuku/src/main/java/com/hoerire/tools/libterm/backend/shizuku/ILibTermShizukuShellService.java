package com.hoerire.tools.libterm.backend.shizuku;

/** 手写替代 aidl 生成物（本机无 aarch64 aidl 可执行文件）。 */
public interface ILibTermShizukuShellService extends android.os.IInterface {
    public static class Default implements com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService {
        @Override public long openSession(java.lang.String cwd, com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback callback) throws android.os.RemoteException { return 0L; }
        @Override public void write(long sessionId, byte[] data) throws android.os.RemoteException { }
        @Override public void close(long sessionId) throws android.os.RemoteException { }
        @Override public android.os.IBinder asBinder() { return null; }
    }

    public static abstract class Stub extends android.os.Binder implements com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService {
        private static final java.lang.String DESCRIPTOR = "com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService";

        public Stub() { this.attachInterface(this, DESCRIPTOR); }

        public static com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService asInterface(android.os.IBinder obj) {
            if ((obj == null)) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService))) {
                return ((com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService) iin);
            }
            return new com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService.Stub.Proxy(obj);
        }

        @Override public android.os.IBinder asBinder() { return this; }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            java.lang.String descriptor = DESCRIPTOR;
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(descriptor); return true; }
                case TRANSACTION_openSession: {
                    data.enforceInterface(descriptor);
                    java.lang.String _arg0 = data.readString();
                    com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback _arg1 = com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback.Stub.asInterface(data.readStrongBinder());
                    long _result = this.openSession(_arg0, _arg1);
                    reply.writeNoException();
                    reply.writeLong(_result);
                    return true;
                }
                case TRANSACTION_write: {
                    data.enforceInterface(descriptor);
                    long _arg0 = data.readLong();
                    byte[] _arg1 = data.createByteArray();
                    this.write(_arg0, _arg1);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_close: {
                    data.enforceInterface(descriptor);
                    long _arg0 = data.readLong();
                    this.close(_arg0);
                    reply.writeNoException();
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellService {
            private android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            public java.lang.String getInterfaceDescriptor() { return DESCRIPTOR; }
            @Override public long openSession(java.lang.String cwd, com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback callback) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                long _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(cwd);
                    _data.writeStrongBinder((((callback != null)) ? (callback.asBinder()) : (null)));
                    boolean _status = mRemote.transact(Stub.TRANSACTION_openSession, _data, _reply, 0);
                    if (!_status) { return 0L; }
                    _reply.readException();
                    _result = _reply.readLong();
                } finally { _reply.recycle(); _data.recycle(); }
                return _result;
            }
            @Override public void write(long sessionId, byte[] data) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(sessionId);
                    _data.writeByteArray(data);
                    boolean _status = mRemote.transact(Stub.TRANSACTION_write, _data, _reply, 0);
                    if (!_status) { return; }
                    _reply.readException();
                } finally { _reply.recycle(); _data.recycle(); }
            }
            @Override public void close(long sessionId) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(sessionId);
                    boolean _status = mRemote.transact(Stub.TRANSACTION_close, _data, _reply, 0);
                    if (!_status) { return; }
                    _reply.readException();
                } finally { _reply.recycle(); _data.recycle(); }
            }
        }
        static final int TRANSACTION_openSession = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_write = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_close = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    }

    public long openSession(java.lang.String cwd, com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback callback) throws android.os.RemoteException;
    public void write(long sessionId, byte[] data) throws android.os.RemoteException;
    public void close(long sessionId) throws android.os.RemoteException;
}
