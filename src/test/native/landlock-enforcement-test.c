#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

/* Runs as a child of the Landlock helper, validating inherited restrictions. */
int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "Usage: landlock-enforcement-test ALLOWED_FILE DENIED_FILE\n");
        return 10;
    }
    int allowed = open(argv[1], O_CREAT | O_WRONLY | O_TRUNC, 0600);
    if (allowed < 0) {
        fprintf(stderr, "Could not write inside website root: %s\n", strerror(errno));
        return 11;
    }
    if (write(allowed, "safe", 4) != 4) {
        close(allowed);
        return 12;
    }
    close(allowed);
    int denied = open(argv[2], O_RDONLY);
    if (denied >= 0) {
        close(denied);
        fprintf(stderr, "Landlock escape: read of outside file succeeded!\n");
        return 13;
    }
    if (errno != EACCES && errno != EPERM) {
        fprintf(stderr, "Expected EACCES/EPERM for outside file, got: %s\n", strerror(errno));
        return 14;
    }
    puts("PASS: website write allowed, outside Minecraft-like file denied");
    return 0;
}
