#!/usr/bin/env bash
# Build whisper.cpp from source (no Metal) and compile the FFI shim.
#
# Usage:
#   ./build-whisper.sh [version]     # default: 1.7.3
#
# Output goes to ~/.casehub/native/whisper/no-metal/
# The shim requires whisper.h from the whisper.cpp source tree.
set -euo pipefail

VERSION="${1:-1.7.3}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../../../tmp/whisper-build"
OUTPUT_DIR="${HOME}/.casehub/native/whisper/no-metal"

mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

# --- Clone or update whisper.cpp ---
WHISPER_SRC="${BUILD_DIR}/whisper.cpp"
if [ -d "$WHISPER_SRC" ]; then
    git -C "$WHISPER_SRC" fetch --tags
    git -C "$WHISPER_SRC" checkout "v${VERSION}"
else
    git clone --depth 1 --branch "v${VERSION}" \
        https://github.com/ggerganov/whisper.cpp.git "$WHISPER_SRC"
fi

# --- Build whisper.cpp (no Metal, shared libs) ---
cmake -S "$WHISPER_SRC" -B "${BUILD_DIR}/build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DGGML_METAL=OFF \
    -DBUILD_SHARED_LIBS=ON \
    -DWHISPER_BUILD_EXAMPLES=OFF \
    -DWHISPER_BUILD_TESTS=OFF
cmake --build "${BUILD_DIR}/build" --config Release -j "$(sysctl -n hw.logicalcpu)"

# --- Copy built libraries ---
find "${BUILD_DIR}/build" -name '*.dylib' -exec cp {} "$OUTPUT_DIR/" \;

# --- Build the shim ---
cc -shared -o "${OUTPUT_DIR}/libwhisper_shim.dylib" \
    -I "${WHISPER_SRC}/include" \
    -I "${WHISPER_SRC}/ggml/include" \
    -L "$OUTPUT_DIR" -lwhisper \
    "${SCRIPT_DIR}/whisper_shim.c"

# --- Patch rpaths so all libs find each other via @loader_path ---
for lib in "$OUTPUT_DIR"/*.dylib; do
    install_name_tool -add_rpath @loader_path "$lib" 2>/dev/null || true
done

echo "Done — whisper ${VERSION} + shim built at ${OUTPUT_DIR}"
