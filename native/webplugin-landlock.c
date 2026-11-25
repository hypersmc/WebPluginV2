#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/landlock.h>
#include <stdbool.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <sys/types.h>

#ifndef O_PATH
#define O_PATH 010000000
#endif

struct path_rule {
    const char *path;
    enum { RULE_RO, RULE_RX, RULE_RW } mode;
};

static int landlock_abi(void) {
    errno = 0;
    return (int) syscall(SYS_landlock_create_ruleset, NULL, 0,
                         LANDLOCK_CREATE_RULESET_VERSION);
}

static uint64_t handled_fs_for_abi(int abi);

static const char *probe_status(int err) {
    switch (err) {
        case ENOSYS: return "UNSUPPORTED_KERNEL";
        case EOPNOTSUPP: return "DISABLED_BY_KERNEL";
        case EPERM:
        case EACCES: return "BLOCKED_BY_CONTAINER";
        default: return "PROBE_FAILED";
    }
}

static int probe(void) {
    int abi = landlock_abi();
    if (abi < 1) {
        int err = errno;
        printf("%s %d %s\n", probe_status(err), err, strerror(err));
        if (err == ENOSYS) return 10;
        if (err == EOPNOTSUPP) return 11;
        if (err == EPERM || err == EACCES) return 12;
        return 13;
    }

    /*
     * Do more than query the ABI. Some container policies may permit the ABI
     * query while blocking ruleset creation or restrict_self. The probe runs
     * in its own short-lived child process, so applying an empty ruleset here
     * cannot restrict the ServerFabric JVM.
     */
    struct landlock_ruleset_attr ruleset = {
        .handled_access_fs = handled_fs_for_abi(abi),
    };
    int ruleset_fd = (int) syscall(SYS_landlock_create_ruleset, &ruleset,
                                   sizeof(ruleset), 0);
    if (ruleset_fd < 0) {
        int err = errno;
        printf("%s %d %s\n", probe_status(err), err, strerror(err));
        return (err == EPERM || err == EACCES) ? 12 : 13;
    }
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
        int err = errno;
        close(ruleset_fd);
        printf("%s %d %s\n", probe_status(err), err, strerror(err));
        return (err == EPERM || err == EACCES) ? 12 : 13;
    }
    if (syscall(SYS_landlock_restrict_self, ruleset_fd, 0) != 0) {
        int err = errno;
        close(ruleset_fd);
        printf("%s %d %s\n", probe_status(err), err, strerror(err));
        return (err == EPERM || err == EACCES) ? 12 : 13;
    }
    close(ruleset_fd);
    printf("AVAILABLE %d\n", abi);
    return 0;
}

static uint64_t handled_fs_for_abi(int abi) {
    uint64_t rights = LANDLOCK_ACCESS_FS_EXECUTE |
                      LANDLOCK_ACCESS_FS_WRITE_FILE |
                      LANDLOCK_ACCESS_FS_READ_FILE |
                      LANDLOCK_ACCESS_FS_READ_DIR |
                      LANDLOCK_ACCESS_FS_REMOVE_DIR |
                      LANDLOCK_ACCESS_FS_REMOVE_FILE |
                      LANDLOCK_ACCESS_FS_MAKE_CHAR |
                      LANDLOCK_ACCESS_FS_MAKE_DIR |
                      LANDLOCK_ACCESS_FS_MAKE_REG |
                      LANDLOCK_ACCESS_FS_MAKE_SOCK |
                      LANDLOCK_ACCESS_FS_MAKE_FIFO |
                      LANDLOCK_ACCESS_FS_MAKE_BLOCK |
                      LANDLOCK_ACCESS_FS_MAKE_SYM;
    if (abi >= 2) rights |= LANDLOCK_ACCESS_FS_REFER;
    if (abi >= 3) rights |= LANDLOCK_ACCESS_FS_TRUNCATE;
#ifdef LANDLOCK_ACCESS_FS_IOCTL_DEV
    if (abi >= 5) rights |= LANDLOCK_ACCESS_FS_IOCTL_DEV;
#endif
    return rights;
}

static uint64_t rights_for_rule(int abi, int mode, bool directory) {
    uint64_t rights = LANDLOCK_ACCESS_FS_READ_FILE;
    if (directory) rights |= LANDLOCK_ACCESS_FS_READ_DIR;
    if (mode == RULE_RX) rights |= LANDLOCK_ACCESS_FS_EXECUTE;
    if (mode == RULE_RW) {
        rights |= LANDLOCK_ACCESS_FS_WRITE_FILE;
        if (directory) {
            rights |= LANDLOCK_ACCESS_FS_REMOVE_DIR |
                      LANDLOCK_ACCESS_FS_REMOVE_FILE |
                      LANDLOCK_ACCESS_FS_MAKE_DIR |
                      LANDLOCK_ACCESS_FS_MAKE_REG |
                      LANDLOCK_ACCESS_FS_MAKE_SOCK |
                      LANDLOCK_ACCESS_FS_MAKE_FIFO |
                      LANDLOCK_ACCESS_FS_MAKE_SYM;
            if (abi >= 2) rights |= LANDLOCK_ACCESS_FS_REFER;
        }
        if (abi >= 3) rights |= LANDLOCK_ACCESS_FS_TRUNCATE;
#ifdef LANDLOCK_ACCESS_FS_IOCTL_DEV
        if (abi >= 5 && !directory) rights |= LANDLOCK_ACCESS_FS_IOCTL_DEV;
#endif
    }
    return rights & handled_fs_for_abi(abi);
}

static int add_path_rule(int ruleset_fd, int abi, const struct path_rule *rule) {
    int fd = open(rule->path, O_PATH | O_CLOEXEC);
    if (fd < 0) {
        fprintf(stderr, "landlock: cannot open allowed path %s: %s\n",
                rule->path, strerror(errno));
        return -1;
    }
    struct stat st;
    if (fstat(fd, &st) != 0) {
        fprintf(stderr, "landlock: cannot stat allowed path %s: %s\n",
                rule->path, strerror(errno));
        close(fd);
        return -1;
    }
    struct landlock_path_beneath_attr attr = {
        .allowed_access = rights_for_rule(abi, rule->mode, S_ISDIR(st.st_mode)),
        .parent_fd = fd,
    };
    int result = (int) syscall(SYS_landlock_add_rule, ruleset_fd,
                               LANDLOCK_RULE_PATH_BENEATH, &attr, 0);
    if (result != 0) {
        fprintf(stderr, "landlock: cannot allow path %s: %s\n",
                rule->path, strerror(errno));
    }
    close(fd);
    return result;
}

static void usage(const char *program) {
    fprintf(stderr,
            "Usage:\n"
            "  %s --probe\n"
            "  %s --signal-group PGID TERM|KILL|QUIT|INT\n"
            "  %s [--no-sandbox] [--new-session] [--ro PATH] [--rx PATH] [--rw PATH] -- COMMAND [ARG...]\n",
            program, program, program);
}

static int parse_signal(const char *value) {
    if (strcmp(value, "TERM") == 0) return SIGTERM;
    if (strcmp(value, "KILL") == 0) return SIGKILL;
    if (strcmp(value, "QUIT") == 0) return SIGQUIT;
    if (strcmp(value, "INT") == 0) return SIGINT;
    return 0;
}

static int signal_group(const char *pgid_value, const char *signal_value) {
    char *end = NULL;
    errno = 0;
    long parsed = strtol(pgid_value, &end, 10);
    int sig = parse_signal(signal_value);
    if (errno != 0 || end == pgid_value || *end != '\0' || parsed <= 1 || sig == 0) {
        fprintf(stderr, "landlock: invalid process group or signal\n");
        return 64;
    }
    if (kill(-(pid_t) parsed, sig) != 0) {
        if (errno == ESRCH) return 0;
        fprintf(stderr, "landlock: cannot signal process group %ld: %s\n", parsed, strerror(errno));
        return 74;
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--probe") == 0) return probe();
    if (argc == 4 && strcmp(argv[1], "--signal-group") == 0) {
        return signal_group(argv[2], argv[3]);
    }

    struct path_rule *rules = calloc((size_t) argc, sizeof(*rules));
    if (!rules) {
        perror("calloc");
        return 70;
    }
    int rule_count = 0;
    int command_index = -1;
    bool no_sandbox = false;
    bool new_session = false;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--") == 0) {
            command_index = i + 1;
            break;
        }
        if (strcmp(argv[i], "--no-sandbox") == 0) {
            no_sandbox = true;
            continue;
        }
        if (strcmp(argv[i], "--new-session") == 0) {
            new_session = true;
            continue;
        }
        if ((strcmp(argv[i], "--ro") == 0 ||
             strcmp(argv[i], "--rx") == 0 ||
             strcmp(argv[i], "--rw") == 0) && i + 1 < argc) {
            rules[rule_count].mode = strcmp(argv[i], "--rw") == 0 ? RULE_RW :
                                     strcmp(argv[i], "--rx") == 0 ? RULE_RX : RULE_RO;
            rules[rule_count].path = argv[++i];
            rule_count++;
            continue;
        }
        usage(argv[0]);
        free(rules);
        return 64;
    }
    if (command_index < 0 || command_index >= argc) {
        usage(argv[0]);
        free(rules);
        return 64;
    }

    if (!no_sandbox) {
        int abi = landlock_abi();
        if (abi < 1) {
            fprintf(stderr, "landlock: %s: %s\n", probe_status(errno), strerror(errno));
            free(rules);
            return 65;
        }

        struct landlock_ruleset_attr ruleset = {
            .handled_access_fs = handled_fs_for_abi(abi),
        };
        int ruleset_fd = (int) syscall(SYS_landlock_create_ruleset, &ruleset,
                                       sizeof(ruleset), 0);
        if (ruleset_fd < 0) {
            fprintf(stderr, "landlock: cannot create ruleset: %s\n", strerror(errno));
            free(rules);
            return 66;
        }

        for (int i = 0; i < rule_count; i++) {
            if (add_path_rule(ruleset_fd, abi, &rules[i]) != 0) {
                close(ruleset_fd);
                free(rules);
                return 67;
            }
        }

        if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
            fprintf(stderr, "landlock: cannot set no_new_privs: %s\n", strerror(errno));
            close(ruleset_fd);
            free(rules);
            return 68;
        }
        if (syscall(SYS_landlock_restrict_self, ruleset_fd, 0) != 0) {
            fprintf(stderr, "landlock: cannot restrict process: %s\n", strerror(errno));
            close(ruleset_fd);
            free(rules);
            return 69;
        }
        close(ruleset_fd);
    }

    if (new_session) {
        if (setsid() < 0 && setpgid(0, 0) != 0) {
            fprintf(stderr, "landlock: cannot create managed process group: %s\n", strerror(errno));
            free(rules);
            return 71;
        }
    }
    free(rules);

    execvp(argv[command_index], &argv[command_index]);
    fprintf(stderr, "landlock: cannot execute %s: %s\n",
            argv[command_index], strerror(errno));
    return 127;
}
