# Security Policy

## Reporting a vulnerability

Please do not open a public issue for vulnerabilities that expose files, execute commands, leak logs/config, or weaken SSL/private-key handling.

Send a private report to the maintainer listed in the README, including:

- affected version/commit
- server platform and OS
- reproduction steps
- expected impact
- suggested fix, if known

## Security-sensitive areas

- Static file serving from the plugin data folder
- PHP/NGINX installation and process execution
- Log upload/redaction
- SSL certificate/private-key handling
- Auto-download of native runtime archives
