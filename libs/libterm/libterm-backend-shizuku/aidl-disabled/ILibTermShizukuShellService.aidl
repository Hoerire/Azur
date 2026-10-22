package com.hoerire.tools.libterm.backend.shizuku;

import com.hoerire.tools.libterm.backend.shizuku.ILibTermShizukuShellCallback;

interface ILibTermShizukuShellService {
    long openSession(String cwd, ILibTermShizukuShellCallback callback);
    void write(long sessionId, in byte[] data);
    void close(long sessionId);
}
