/* 自研最小 PTY 底座：只用 aarch64 裸 syscall，不链接 libc，无第三方代码。 */
typedef long i64;

static inline i64 zsys3(i64 n, i64 a, i64 b, i64 c) {
    register i64 x8 __asm__("x8") = n;
    register i64 x0 __asm__("x0") = a;
    register i64 x1 __asm__("x1") = b;
    register i64 x2 __asm__("x2") = c;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2) : "memory");
    return x0;
}

#define SYS_openat 56
#define SYS_ioctl  29
#define SYS_close  57
#define AT_FDCWD   (-100)
#define O_RDWR     2
#define O_NOCTTY   0400
#define O_CLOEXEC  02000000
#define TIOCSPTLCK 0x40045431UL
#define TIOCGPTN   0x80045430UL
#define TIOCSWINSZ 0x5414UL

__attribute__((visibility("default"))) int zafiro_open_pty(void) {
    int fd = (int)zsys3(SYS_openat, AT_FDCWD, (i64)"/dev/ptmx", O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (fd < 0) return -1;
    int zero = 0;
    if (zsys3(SYS_ioctl, fd, (i64)TIOCSPTLCK, (i64)&zero) != 0) {
        zsys3(SYS_close, fd, 0, 0);
        return -1;
    }
    return fd;
}

__attribute__((visibility("default"))) int zafiro_pts_index(int fd) {
    int n = -1;
    if (zsys3(SYS_ioctl, fd, (i64)TIOCGPTN, (i64)&n) != 0) return -1;
    return n;
}

__attribute__((visibility("default"))) int zafiro_set_winsize(int fd, int rows, int cols) {
    unsigned short ws[4];
    ws[0] = (unsigned short)rows;
    ws[1] = (unsigned short)cols;
    ws[2] = 0;
    ws[3] = 0;
    return (int)zsys3(SYS_ioctl, fd, (i64)TIOCSWINSZ, (i64)ws);
}

/* JNI 入口：不放 jni.h，JNIEnv/jobject 一律忽略，只收发 int。 */
__attribute__((visibility("default"))) int Java_com_hoerire_tools_zafiro_chat_agentic_shell_PtyNative_nativeOpenPty(void *e, void *c) {
    (void)e; (void)c;
    return zafiro_open_pty();
}

__attribute__((visibility("default"))) int Java_com_hoerire_tools_zafiro_chat_agentic_shell_PtyNative_nativePtsIndex(void *e, void *c, int fd) {
    (void)e; (void)c;
    return zafiro_pts_index(fd);
}

__attribute__((visibility("default"))) int Java_com_hoerire_tools_zafiro_chat_agentic_shell_PtyNative_nativeSetWinSize(void *e, void *c, int fd, int rows, int cols) {
    (void)e; (void)c;
    return zafiro_set_winsize(fd, rows, cols);
}
