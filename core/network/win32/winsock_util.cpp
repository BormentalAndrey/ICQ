#include "winsock_util.h"

bool init_network() {
#ifdef PLATFORM_WIN
    WSADATA wsaData;
    return WSAStartup(MAKEWORD(2, 2), &wsaData) == 0;
#else
    return true;
#endif
}

void set_socket_blocking(int fd, bool blocking) {
#ifdef PLATFORM_WIN
    u_long mode = blocking ? 0 : 1;
    ioctlsocket(fd, FIONBIO, &mode);
#else
    int flags = fcntl(fd, F_GETFL, 0);
    if (blocking) flags &= ~O_NONBLOCK;
    else flags |= O_NONBLOCK;
    fcntl(fd, F_SETFL, flags);
#endif
}

void set_socket_timeouts(int fd, int timeout_ms) {
#ifdef PLATFORM_WIN
    DWORD tv = timeout_ms;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (char*)&tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, (char*)&tv, sizeof(tv));
#else
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
#endif
}
