#!/usr/bin/env bash
set -euo pipefail

# WebPlugin Linux PHP runtime builder.
#
# This is intentionally different from the old PocketMine-oriented builder:
# - PHP core is built with PHP-FPM and a small base feature set.
# - Optional modules are built as shared .so extensions.
# - Shared extensions are packaged into bin/php8/extension-packs/<extension>/.
# - Each pack has extension.properties so WebPlugin can generate php.ini and LD_LIBRARY_PATH.
#
# Output:
#   dist/WebPlugin-linux-PHP${PHP_VERSION}-MODULAR.tar.gz
#   dist/WebPlugin-linux-PHP${PHP_VERSION}-MODULAR.zip
#
# Usage example:
#   ./scripts/build-webplugin-php-runtime.sh -t linux64 -j 8

PHP_VERSION="${PHP_VERSION:-8.4.8}"
ZLIB_VERSION="${ZLIB_VERSION:-1.3.1}"
ONIG_VERSION="${ONIG_VERSION:-6.9.10}"
OPENSSL_VERSION="${OPENSSL_VERSION:-3.5.0}"
CURL_VERSION="${CURL_VERSION:-curl-8_14_1}"
LIBXML_VERSION="${LIBXML_VERSION:-2.12.10}"
LIBZIP_VERSION="${LIBZIP_VERSION:-1.11.3}"
SQLITE3_VERSION="${SQLITE3_VERSION:-3500200}"
PCRE2_VERSION="${PCRE2_VERSION:-10.45}"
LIBPNG_VERSION="${LIBPNG_VERSION:-1.6.47}"
LIBJPEG_VERSION="${LIBJPEG_VERSION:-9f}"
ICU_VERSION="${ICU_VERSION:-77-1}"

# Profiles are intentionally handled by packaging/loading, not by stripping built extensions.
# Build the runtime with the broad common web set, then let WebPlugin config decide
# which extension-packs are enabled in generated php.ini at server startup.
PHP_EXTENSION_PROFILE="${PHP_EXTENSION_PROFILE:-common}"

DIR="$(pwd)"
BUILD_DIR="$DIR/build/php-runtime"
SRC_DIR="$BUILD_DIR/src"
PREFIX="$BUILD_DIR/prefix"
PACKAGE_ROOT="$BUILD_DIR/package"
INSTALL_DIR="$PACKAGE_ROOT/bin/php8"
DIST_DIR="$DIR/dist"
DOWNLOAD_CACHE="${DOWNLOAD_CACHE:-$DIR/.cache/webplugin-runtime}"
THREADS=1
TARGET="linux64"
ENABLE_GD="yes"
# Bundle glibc/loader for Minecraft server containers which may not have a compatible host glibc.
# Build this runtime on the oldest Linux/glibc you want to support.
BUNDLE_GLIBC="${BUNDLE_GLIBC:-yes}"
DO_CLEAN="yes"

while getopts ":t:j:c:n" opt; do
  case "$opt" in
    t) TARGET="$OPTARG" ;;
    j) THREADS="$OPTARG" ;;
    c) DOWNLOAD_CACHE="$OPTARG" ;;
    n) DO_CLEAN="no" ;;
    \?) echo "Invalid option: -$OPTARG" >&2; exit 1 ;;
  esac
done

case "$TARGET" in
  linux|linux64) ;;
  *) echo "Unsupported target '$TARGET'. This builder currently supports linux/linux64 only." >&2; exit 1 ;;
esac

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing build dependency: $1" >&2; exit 1; }
}

for tool in make gcc g++ autoconf automake libtool m4 bison re2c pkg-config cmake git tar gzip sed awk ldd; do
  need "$tool"
done
SYSTEM_CURL=""
SYSTEM_WGET=""
if command -v curl >/dev/null 2>&1; then
  SYSTEM_CURL="$(command -v curl)"
elif command -v wget >/dev/null 2>&1; then
  SYSTEM_WGET="$(command -v wget)"
else
  echo "Missing build dependency: curl or wget" >&2
  exit 1
fi

mkdir -p "$SRC_DIR" "$PREFIX" "$INSTALL_DIR" "$DIST_DIR" "$DOWNLOAD_CACHE"

log() { printf '[WebPlugin PHP Runtime] %s\n' "$*" >&2; }

fetch() {
  local url="$1"
  local out="$DOWNLOAD_CACHE/${url##*/}"
  mkdir -p "$(dirname "$out")"
  if [ ! -f "$out" ]; then
    log "Downloading $url"
    if [ -n "$SYSTEM_CURL" ]; then
      "$SYSTEM_CURL" --fail --location --show-error --silent "$url" -o "$out"
    else
      "$SYSTEM_WGET" -q "$url" -O "$out"
    fi
  fi
  printf '%s\n' "$out"
}

fetch_github() {
  local repo="$1"
  local ref="$2"
  fetch "https://github.com/$repo/archive/$ref.tar.gz"
}

extract_to() {
  local archive="$1"
  local dest="$2"
  rm -rf "$dest"
  mkdir -p "$dest"
  tar -xf "$archive" -C "$dest" --strip-components=1
}

export CC="${CC:-gcc}"
export CXX="${CXX:-g++}"
export CFLAGS="${CFLAGS:--O2 -fPIC -m64}"
export CXXFLAGS="${CXXFLAGS:-$CFLAGS}"
export LDFLAGS="${LDFLAGS:--Wl,-rpath,'\$ORIGIN/../lib' -Wl,-rpath-link,'\$ORIGIN/../lib'}"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export PATH="$PATH:$PREFIX/bin"

build_zlib() {
  log "Building zlib $ZLIB_VERSION"
  local dir="$SRC_DIR/zlib"
  extract_to "$(fetch_github madler/zlib "v$ZLIB_VERSION")" "$dir"
  (cd "$dir" && ./configure --prefix="$PREFIX" --shared && make -j "$THREADS" && make install)
  rm -f "$PREFIX/lib/libz.a"
}

build_oniguruma() {
  log "Building oniguruma $ONIG_VERSION"
  local dir="$SRC_DIR/oniguruma"
  extract_to "$(fetch_github kkos/oniguruma "v$ONIG_VERSION")" "$dir"
  (cd "$dir" && ./autogen.sh && ./configure --prefix="$PREFIX" --enable-shared --disable-static && make -j "$THREADS" && make install)
}

build_openssl() {
  log "Building OpenSSL $OPENSSL_VERSION"
  local dir="$SRC_DIR/openssl"
  extract_to "$(fetch_github openssl/openssl "openssl-$OPENSSL_VERSION")" "$dir"
  (cd "$dir" && ./Configure linux-x86_64 --prefix="$PREFIX" --openssldir="$PREFIX" --libdir=lib shared no-asm no-hw no-engine && make -j "$THREADS" && make install_sw)
}

build_curl() {
  log "Building curl $CURL_VERSION"
  local dir="$SRC_DIR/curl"
  extract_to "$(fetch_github curl/curl "$CURL_VERSION")" "$dir"
  (cd "$dir" && ./buildconf --force && ./configure \
    --prefix="$PREFIX" \
    --enable-shared --disable-static \
    --enable-http --enable-file --enable-ipv6 --enable-threaded-resolver \
    --disable-dict --disable-gopher --disable-imap --disable-pop3 --disable-rtsp \
    --disable-smtp --disable-telnet --disable-tftp --disable-ldap --disable-ldaps \
    --without-libpsl --without-libidn --without-libidn2 --without-brotli --without-nghttp2 --without-zstd \
    --with-zlib="$PREFIX" --with-ssl="$PREFIX" \
    && make -j "$THREADS" && make install)
}

build_libxml2() {
  log "Building libxml2 $LIBXML_VERSION"
  local dir="$SRC_DIR/libxml2"
  extract_to "$(fetch_github GNOME/libxml2 "v$LIBXML_VERSION")" "$dir"
  (cd "$dir" && ./autogen.sh --prefix="$PREFIX" --without-python --without-lzma --with-zlib="$PREFIX" --enable-shared=yes --enable-static=no && make -j "$THREADS" && make install)
}

build_sqlite3() {
  log "Building sqlite $SQLITE3_VERSION"
  local dir="$SRC_DIR/sqlite3"
  extract_to "$(fetch "https://www.sqlite.org/2025/sqlite-autoconf-$SQLITE3_VERSION.tar.gz")" "$dir"
  # SQLite 3.50+ uses autosetup. Shared library output is enabled by default;
  # --enable-shared is no longer accepted.
  (cd "$dir" && ./configure --prefix="$PREFIX" --disable-static --soname=legacy --disable-static-shell && make -j "$THREADS" && make install)
}

build_libzip() {
  log "Building libzip $LIBZIP_VERSION"
  local dir="$SRC_DIR/libzip"
  extract_to "$(fetch_github nih-at/libzip "v$LIBZIP_VERSION")" "$dir"
  (cd "$dir" && cmake . \
    -DCMAKE_PREFIX_PATH="$PREFIX" \
    -DCMAKE_INSTALL_PREFIX="$PREFIX" \
    -DCMAKE_INSTALL_LIBDIR=lib \
    -DBUILD_SHARED_LIBS=ON \
    -DBUILD_TOOLS=OFF -DBUILD_REGRESS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_DOC=OFF \
    -DENABLE_BZIP2=OFF -DENABLE_COMMONCRYPTO=OFF -DENABLE_GNUTLS=OFF \
    -DENABLE_MBEDTLS=OFF -DENABLE_LZMA=OFF -DENABLE_ZSTD=OFF \
    && make -j "$THREADS" && make install)
}

build_libpng() {
  log "Building libpng $LIBPNG_VERSION"
  local dir="$SRC_DIR/libpng"
  extract_to "$(fetch "https://download.sourceforge.net/libpng/libpng-$LIBPNG_VERSION.tar.gz")" "$dir"
  (cd "$dir" && ./configure --prefix="$PREFIX" --enable-shared=yes --enable-static=no && make -j "$THREADS" && make install)
}

build_libjpeg() {
  log "Building libjpeg $LIBJPEG_VERSION"
  local dir="$SRC_DIR/libjpeg"
  extract_to "$(fetch "https://ijg.org/files/jpegsrc.v$LIBJPEG_VERSION.tar.gz")" "$dir"
  (cd "$dir" && ./configure --prefix="$PREFIX" --enable-shared=yes --enable-static=no && make -j "$THREADS" && make install)
}

build_pcre2() {
  log "Building PCRE2 $PCRE2_VERSION"
  local dir="$SRC_DIR/pcre2"
  extract_to "$(fetch "https://github.com/PCRE2Project/pcre2/releases/download/pcre2-$PCRE2_VERSION/pcre2-$PCRE2_VERSION.tar.gz")" "$dir"
  (cd "$dir" && ./configure \
    --prefix="$PREFIX" \
    --enable-shared \
    --disable-static \
    --enable-pcre2-8 \
    --disable-pcre2-16 \
    --disable-pcre2-32 \
    --enable-jit \
    && make -j "$THREADS" && make install)
}

build_icu() {
  log "Building ICU $ICU_VERSION for PHP intl"
  local dir="$SRC_DIR/icu"
  extract_to "$(fetch_github unicode-org/icu "release-$ICU_VERSION")" "$dir"
  (cd "$dir/icu4c/source" && ./configure \
    --prefix="$PREFIX" \
    --enable-shared \
    --disable-static \
    --disable-samples \
    --disable-tests \
    && make -j "$THREADS" && make install)
}

build_php() {
  log "Building PHP $PHP_VERSION with shared optional extensions"
  local dir="$SRC_DIR/php"
  extract_to "$(fetch_github php/php-src "php-$PHP_VERSION")" "$dir"
  (cd "$dir" && ./buildconf --force && \
    test -f "$PREFIX/lib/pkgconfig/libpcre2-8.pc" && \
    PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig" PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig" pkg-config --modversion libpcre2-8 >/dev/null && \
    env \
    PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig" \
    PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig" \
    PCRE2_CFLAGS="-I$PREFIX/include" \
    PCRE2_LIBS="-L$PREFIX/lib -lpcre2-8" \
    ./configure \
    --prefix="$INSTALL_DIR" \
    --exec-prefix="$INSTALL_DIR" \
    --with-config-file-path="$INSTALL_DIR/etc" \
    --with-config-file-scan-dir="$INSTALL_DIR/etc/conf.d" \
    --enable-cli \
    --enable-fpm \
    --disable-cgi \
    --disable-phpdbg \
    --without-pear \
    --with-pic \
    --enable-shared=yes \
    --enable-static=no \
    --with-openssl=shared,"$PREFIX" \
    --with-curl=shared,"$PREFIX" \
    --with-zlib=shared,"$PREFIX" \
    --enable-mbstring=shared \
    --enable-mbregex \
    --enable-intl=shared \
    --with-libxml="$PREFIX" \
    --enable-dom=shared \
    --enable-simplexml=shared \
    --enable-xml=shared \
    --enable-xmlreader=shared \
    --enable-xmlwriter=shared \
    --enable-fileinfo=shared \
    --with-zip=shared,"$PREFIX" \
    --with-pdo-sqlite=shared,"$PREFIX" \
    --with-sqlite3=shared,"$PREFIX" \
    --with-pdo-mysql=shared,mysqlnd \
    --with-mysqli=shared,mysqlnd \
    --enable-bcmath=shared \
    --enable-sockets=shared \
    --enable-ftp=shared \
    --enable-exif=shared \
    --enable-calendar=shared \
    --enable-ctype=shared \
    --enable-pcntl=shared \
    --enable-shmop=shared \
    --enable-sysvsem=shared \
    --enable-sysvshm=shared \
    --enable-sysvmsg=shared \
    --enable-soap=shared \
    --enable-tokenizer=shared \
    --enable-session \
    --enable-phar \
    --enable-opcache=shared \
    ${ENABLE_GD:+--enable-gd=shared --with-jpeg="$PREFIX"} \
    && make -j "$THREADS" && make install)
}

is_glibc_core_dep() {
  case "$1" in
    /lib*/ld-linux-*|/lib*/libc.so.*|/lib*/libpthread.so.*|/lib*/libdl.so.*|/lib*/libm.so.*|/lib*/librt.so.*|/lib*/libresolv.so.*|/lib*/libnss_*.so.*|/lib*/libgcc_s.so.*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

copy_file_once() {
  local src="$1"
  local dest_dir="$2"
  [ -f "$src" ] || return 0
  mkdir -p "$dest_dir"
  cp -L -n "$src" "$dest_dir/" 2>/dev/null || true
}

copy_prefix_soname() {
  local soname="$1"
  local dest_dir="$2"
  local found="no"
  shopt -s nullglob
  for candidate in "$PREFIX/lib/$soname" "$PREFIX/lib/$soname".*; do
    copy_file_once "$candidate" "$dest_dir"
    found="yes"
  done
  shopt -u nullglob
  [ "$found" = "yes" ]
}

copy_deps() {
  local target_dir="$1"
  shift
  mkdir -p "$target_dir"
  for binary in "$@"; do
    [ -f "$binary" ] || continue

    # Copy resolved non-glibc dependencies.
    ldd "$binary" 2>/dev/null | awk '/=> \/|^\// { for(i=1;i<=NF;i++) if ($i ~ /^\//) print $i }' | while read -r dep; do
      [ -f "$dep" ] || continue
      if is_glibc_core_dep "$dep"; then
        continue
      fi
      copy_file_once "$dep" "$target_dir"
    done

    # ldd reports prefix-built libs as "not found" when the extension has not yet been
    # given an rpath and LD_LIBRARY_PATH is not set. Resolve those from our prefix.
    ldd "$binary" 2>/dev/null | awk '/=> not found/ { print $1 }' | while read -r soname; do
      copy_prefix_soname "$soname" "$target_dir" || true
    done
  done
}

find_glibc_loader() {
  for candidate in \
    /lib64/ld-linux-x86-64.so.2 \
    /lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 \
    /usr/lib64/ld-linux-x86-64.so.2 \
    /usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2; do
    if [ -f "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  ldd "$INSTALL_DIR/bin/php" 2>/dev/null | awk '/ld-linux/ { for(i=1;i<=NF;i++) if ($i ~ /^\//) { print $i; exit } }'
}

copy_glibc_named_runtime_libs() {
  local target_dir="$1"
  mkdir -p "$target_dir"

  # Some required glibc helper libraries are loaded by other native binaries
  # such as NGINX, but they do not always appear in PHP/php-fpm ldd output on
  # newer distributions where libpthread/libdl are folded into libc. Copy the
  # common loader/runtime set explicitly so bundled libc is not mixed with host
  # libdl/libpthread at runtime.
  shopt -s nullglob
  for candidate in \
    /lib64/ld-linux-x86-64.so.2 \
    /lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 \
    /usr/lib64/ld-linux-x86-64.so.2 \
    /usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 \
    /lib/x86_64-linux-gnu/libc.so.* \
    /lib/x86_64-linux-gnu/libpthread.so.* \
    /lib/x86_64-linux-gnu/libdl.so.* \
    /lib/x86_64-linux-gnu/librt.so.* \
    /lib/x86_64-linux-gnu/libm.so.* \
    /lib/x86_64-linux-gnu/libresolv.so.* \
    /lib/x86_64-linux-gnu/libnss_dns.so.* \
    /lib/x86_64-linux-gnu/libnss_files.so.* \
    /lib/x86_64-linux-gnu/libgcc_s.so.* \
    /usr/lib/x86_64-linux-gnu/libc.so.* \
    /usr/lib/x86_64-linux-gnu/libpthread.so.* \
    /usr/lib/x86_64-linux-gnu/libdl.so.* \
    /usr/lib/x86_64-linux-gnu/librt.so.* \
    /usr/lib/x86_64-linux-gnu/libm.so.* \
    /usr/lib/x86_64-linux-gnu/libresolv.so.* \
    /usr/lib/x86_64-linux-gnu/libnss_dns.so.* \
    /usr/lib/x86_64-linux-gnu/libnss_files.so.* \
    /usr/lib/x86_64-linux-gnu/libgcc_s.so.*; do
    copy_file_once "$candidate" "$target_dir"
  done
  shopt -u nullglob
}

copy_glibc_deps() {
  local target_dir="$1"
  shift
  mkdir -p "$target_dir"

  local loader
  loader="$(find_glibc_loader || true)"
  if [ -n "$loader" ] && [ -f "$loader" ]; then
    copy_file_once "$loader" "$target_dir"
  else
    log "WARNING: Could not find ld-linux-x86-64.so.2. Bundled glibc loader will be missing."
  fi

  copy_glibc_named_runtime_libs "$target_dir"

  for binary in "$@"; do
    [ -f "$binary" ] || continue
    ldd "$binary" 2>/dev/null | awk '/=> \/|^\// { for(i=1;i<=NF;i++) if ($i ~ /^\//) print $i }' | while read -r dep; do
      [ -f "$dep" ] || continue
      if is_glibc_core_dep "$dep"; then
        copy_file_once "$dep" "$target_dir"
      fi
    done
  done

  # NSS/resolver modules are often loaded dynamically and may not appear in ldd,
  # but they are needed for DNS/user/group lookups inside stripped-down containers.
  shopt -s nullglob
  for candidate in \
    /lib/x86_64-linux-gnu/libnss_dns.so.* \
    /lib/x86_64-linux-gnu/libnss_files.so.* \
    /lib/x86_64-linux-gnu/libresolv.so.* \
    /usr/lib/x86_64-linux-gnu/libnss_dns.so.* \
    /usr/lib/x86_64-linux-gnu/libnss_files.so.* \
    /usr/lib/x86_64-linux-gnu/libresolv.so.*; do
    copy_file_once "$candidate" "$target_dir"
  done
  shopt -u nullglob
}

write_pack_manifest() {
  local pack_dir="$1"
  local name="$2"
  local so_name="$3"
  local ini_type="extension"
  [ "$name" = "opcache" ] && ini_type="zend_extension"

  local native_libraries=""
  if [ -d "$pack_dir/libs" ]; then
    native_libraries="$(find "$pack_dir/libs" -maxdepth 1 -type f -printf '%f,' 2>/dev/null | sed 's/,$//')"
  fi

  cat > "$pack_dir/extension.properties" <<MANIFEST
name=$name
ini_type=$ini_type
extension_file=$so_name
library_paths=libs
native_libraries=$native_libraries
MANIFEST
}

package_extension_packs() {
  log "Packaging shared extensions into extension-packs/"
  local ext_dir
  ext_dir="$($INSTALL_DIR/bin/php-config --extension-dir)"
  local packs_root="$INSTALL_DIR/extension-packs"
  mkdir -p "$packs_root"

  shopt -s nullglob
  for so in "$ext_dir"/*.so; do
    local base name pack_dir so_name
    so_name="$(basename "$so")"
    name="${so_name%.so}"
    pack_dir="$packs_root/$name"
    mkdir -p "$pack_dir/libs"
    cp "$so" "$pack_dir/$so_name"
    copy_deps "$pack_dir/libs" "$pack_dir/$so_name"
    if command -v patchelf >/dev/null 2>&1; then
      patchelf --set-rpath '$ORIGIN/libs' "$pack_dir/$so_name" || true
    fi
    write_pack_manifest "$pack_dir" "$name" "$so_name"
  done
  shopt -u nullglob

  rm -rf "$ext_dir"
}

package_base_libs() {
  log "Packaging base runtime libs"
  mkdir -p "$INSTALL_DIR/lib/base"
  copy_deps "$INSTALL_DIR/lib/base" "$INSTALL_DIR/bin/php" "$INSTALL_DIR/sbin/php-fpm"

  # Copy libraries built in our prefix. These are safe to have available to core PHP and extension packs.
  find "$PREFIX/lib" -maxdepth 1 -type f \( -name '*.so' -o -name '*.so.*' \) -exec cp -L -n {} "$INSTALL_DIR/lib/base/" \; || true

  # WebPlugin's current PHP runtime model has historically needed these available even
  # before extension-pack-specific paths are generated. Keep them explicit.
  copy_prefix_soname "libz.so" "$INSTALL_DIR/lib/base" || true
  copy_prefix_soname "libonig.so" "$INSTALL_DIR/lib/base" || true

  local manifest_base_path="lib/base"
  if [ "$BUNDLE_GLIBC" = "yes" ]; then
    log "Packaging bundled glibc loader/libs"
    local glibc_dir="$INSTALL_DIR/lib/glibc/lib"
    mkdir -p "$glibc_dir"

    local extension_files=()
    while IFS= read -r -d '' so; do
      extension_files+=("$so")
    done < <(find "$INSTALL_DIR/extension-packs" -type f -name '*.so' -print0 2>/dev/null || true)

    copy_glibc_deps "$glibc_dir" "$INSTALL_DIR/bin/php" "$INSTALL_DIR/sbin/php-fpm" "${extension_files[@]}"
    manifest_base_path="lib/glibc/lib:lib/base"
  fi

  cat > "$INSTALL_DIR/runtime-manifest.properties" <<MANIFEST
runtime_id=linux-x64-glibc
php_version=$PHP_VERSION
layout=webplugin-modular-v1
extension_profile=$PHP_EXTENSION_PROFILE
extension_packs=extension-packs
base_library_path=$manifest_base_path
glibc_bundled=$BUNDLE_GLIBC
MANIFEST
}

write_php_ini() {
  log "Writing base php.ini"
  mkdir -p "$INSTALL_DIR/etc" "$INSTALL_DIR/etc/conf.d" "$INSTALL_DIR/var/log" "$INSTALL_DIR/var/run"
  cat > "$INSTALL_DIR/etc/php.ini" <<'INI'
memory_limit=512M
date.timezone=UTC
short_open_tag=0
expose_php=0
display_errors=1
display_startup_errors=1
error_reporting=-1
cgi.fix_pathinfo=0
opcache.enable=1
opcache.enable_cli=0
opcache.validate_timestamps=1
opcache.revalidate_freq=0
INI
  # Backwards-compatible path for older WebPlugin code and for humans looking in bin/.
  cp "$INSTALL_DIR/etc/php.ini" "$INSTALL_DIR/bin/php.ini"
}

write_default_fpm_config() {
  log "Writing default php-fpm config"
  mkdir -p "$INSTALL_DIR/etc/php-fpm.d"
  cat > "$INSTALL_DIR/etc/php-fpm.conf" <<'FPM'
[global]
error_log = var/log/php-fpm-error.log
include=etc/php-fpm.d/*.conf
FPM
  cat > "$INSTALL_DIR/etc/php-fpm.d/www.conf" <<'POOL'
[www]
listen = 127.0.0.1:25569
pm = dynamic
pm.max_children = 8
pm.start_servers = 2
pm.min_spare_servers = 1
pm.max_spare_servers = 3
clear_env = no
catch_workers_output = yes
POOL
}

create_archives() {
  log "Creating archives"
  local base="WebPlugin-linux-PHP${PHP_VERSION}-MODULAR"
  (cd "$PACKAGE_ROOT" && tar -czf "$DIST_DIR/$base.tar.gz" bin)
  if command -v zip >/dev/null 2>&1; then
    (cd "$PACKAGE_ROOT" && zip -qr "$DIST_DIR/$base.zip" bin)
  fi
  log "Created $DIST_DIR/$base.tar.gz"
  [ -f "$DIST_DIR/$base.zip" ] && log "Created $DIST_DIR/$base.zip"
}

if [ "$DO_CLEAN" = "yes" ]; then
  rm -rf "$BUILD_DIR"
  mkdir -p "$SRC_DIR" "$PREFIX" "$INSTALL_DIR"
fi

build_zlib
build_oniguruma
build_openssl
build_curl
build_libxml2
build_sqlite3
build_libzip
if [ "$ENABLE_GD" = "yes" ]; then
  build_libpng
  build_libjpeg
fi
build_pcre2
build_icu
build_php
write_php_ini
write_default_fpm_config
package_extension_packs
package_base_libs
create_archives

log "Done. Upload the MODULAR archive to the WebPlugin runtime release/repo."
log "Built extension profile: $PHP_EXTENSION_PROFILE"
log "Available extension packs:"
find "$INSTALL_DIR/extension-packs" -maxdepth 1 -mindepth 1 -type d -printf "  - %f\n" 2>/dev/null | sort >&2 || true
