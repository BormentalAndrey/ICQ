#pragma once

#ifdef PLATFORM_WIN
#include <winsock2.h>
#else
#include <sys/socket.h>
#include <fcntl.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#endif

bool init_network();
void set_socket_blocking(int fd, bool blocking);
void set_socket_timeouts(int fd, int timeout_ms);
