# Linux PHP runtime

WebPlugin's Linux PHP runtime is now treated as one self-contained PHP archive plus one NGINX archive.

The PHP archive keeps the existing upload shape:

```text
bin/php8/
  runtime-manifest.properties
  bin/php
  sbin/php-fpm
  etc/php.ini
  etc/php-fpm.conf.default
  etc/php-fpm.d/www.conf.default
  lib/
    glibc/lib/
    base/
  extension-packs/
    curl/
      curl.so
      libs/
      extension.properties
    mbstring/
      mbstring.so
      libs/
      extension.properties
```

## Runtime manifest

`bin/php8/runtime-manifest.properties` tells WebPlugin where the base native libraries and extension packs are:

```properties
runtime_id=linux-x64-glibc
php_version=8.4.8
layout=webplugin-modular-v1
extension_profile=common
extension_packs=extension-packs
base_library_path=lib/glibc/lib:lib/base
glibc_bundled=yes
```

WebPlugin reads this manifest at startup. If the manifest is missing, it falls back to legacy runtime behavior.

## Extension packs

Every optional PHP module has an `extension.properties` file:

```properties
name=mbstring
ini_type=extension
extension_file=mbstring.so
library_paths=libs
native_libraries=libonig.so.5
```

`ini_type` may be `extension` or `zend_extension`. `opcache` uses `zend_extension`.

## Config-controlled modules

The archive can contain many extension packs, but PHP only loads the extensions listed in `config.yml`:

```yaml
Settings:
  PHPDetectExtensionsFromIni: false
  PHPPreflightCheck: true
  PHPExtensions:
    - opcache
    - openssl
    - zlib
    - curl
    - mbstring
    - pdo_sqlite
    - sqlite3
    - zip
```

On startup WebPlugin:

1. Reads `runtime-manifest.properties`.
2. Discovers `extension-packs/*/extension.properties`.
3. Writes `bin/php8/etc/webplugin.generated.php.ini`.
4. Builds PHP-FPM `LD_LIBRARY_PATH` from manifest base paths plus enabled extension pack `libs/` paths.
5. Starts PHP-FPM through the bundled glibc loader if present.
6. Runs a PHP CLI preflight with `php -m` before starting `php-fpm` when `PHPPreflightCheck` is enabled.
7. Starts NGINX through the same bundled glibc loader and manifest base library path. NGINX does not receive PHP extension-pack library paths, because it never loads PHP `.so` modules.


## NGINX and bundled glibc

NGINX is still a separate archive, but in stripped Minecraft containers it may need the same bundled native runtime as PHP. WebPlugin therefore starts NGINX through the PHP runtime's bundled loader when available:

```bash
phplinux/bin/php8/lib/glibc/lib/ld-linux-x86-64.so.2 \
  --library-path "phplinux/bin/php8/lib/glibc/lib:phplinux/bin/php8/lib/base" \
  nginxlinux/bin/nginx/sbin/nginx -c nginxlinux/bin/nginx/conf/nginx.conf -p nginxlinux/bin/nginx
```

The PHP runtime archive must include a complete matching glibc helper set, not only `libc.so.6` and the loader. At minimum it should include files such as `libdl.so.2`, `libpthread.so.0`, `librt.so.1`, `libm.so.6`, `libresolv.so.2`, and NSS modules when present on the build host. Otherwise the loader can accidentally mix bundled `libc.so.6` with host `libdl.so.2`/`libpthread.so.0`, causing errors like `GLIBC_ABI_DT_X86_64_PLT not found`.

## Installation

The PHP archive is configured by:

```yaml
GithubWebPluginPHPRepo: https://github.com/hypersmc/WebPluginV2Repo
GithubWebPluginPHPRepoFilename: WebPlugin-linux-PHP8.4.8-MODULAR.tar.gz
```

The installer downloads the archive with Java, extracts it with `tar`, and no longer downloads GLIBC, LIBONIG, or LIBZ as separate archives. Those libraries are expected to be included in the PHP runtime archive.

NGINX is still installed from its own archive:

```yaml
GithubWebPluginNGINXRepoFilename: WebPlugin-linux-NGINX-1.27.1-PRECOMPILED.tar.gz
```

## Testing a runtime manually

From an extracted runtime folder:

```bash
cd phplinux/bin/php8
./lib/glibc/lib/ld-linux-x86-64.so.2 \
  --library-path "$PWD/lib/glibc/lib:$PWD/lib/base:$PWD/extension-packs/mbstring/libs" \
  ./bin/php -c etc/php.ini -m
```

The direct `./bin/php` command may fail on containers with different native libraries. The bundled loader path is the intended startup path for this runtime.


Note: `mbregex` is built as part of the `mbstring` extension. WebPlugin accepts `mbregex` as an alias and loads `mbstring`.


## Runtime extension_dir

WebPlugin generated php.ini overrides `extension_dir` to the installed runtime `extension-packs` path so phpinfo does not show the build-machine prefix baked into the PHP binary. Extensions are still loaded by absolute pack paths.
