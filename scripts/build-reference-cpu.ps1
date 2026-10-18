$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$source = Join-Path $root 'artifacts/llama-reference-src'
$revision = & git -C $source rev-parse HEAD
if ($revision -ne '620d2225cc436ab0b001e3200e590e3b286ef9ab') { throw 'Unexpected reference source revision' }
$cpuCmake = Get-Content (Join-Path $source 'ggml/src/ggml-cpu/CMakeLists.txt') -Raw
if (!$cpuCmake.Contains('if (ANDROMEDA_REFERENCE_GENERIC)') -or !$cpuCmake.Contains('target_compile_options(ggml-base PRIVATE ${REFERENCE_FLAGS})')) {
    throw 'Missing private generic-selection patch; see config/reference_cpu_source.patch'
}
$sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$cmake = Join-Path $sdk 'cmake/3.22.1/bin/cmake.exe'
$ninja = Join-Path $sdk 'cmake/3.22.1/bin/ninja.exe'
$toolchain = Join-Path $sdk 'ndk/27.1.12297006/build/cmake/android.toolchain.cmake'
$output = Join-Path $root 'artifacts/llama-reference-generic'
& $cmake -S $source -B $output -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static `
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_BACKEND_DL=OFF `
    -DGGML_NATIVE=OFF -DGGML_CPU_ALL_VARIANTS=OFF -DGGML_OPENMP=OFF `
    -DGGML_CPU_REPACK=OFF -DGGML_CPU_KLEIDIAI=OFF -DGGML_LLAMAFILE=OFF `
    -DGGML_CUDA=OFF -DGGML_VULKAN=OFF -DLLAMA_OPENSSL=OFF `
    -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_UI=OFF `
    -DANDROMEDA_REFERENCE_GENERIC=ON '-DCMAKE_C_FLAGS=' '-DCMAKE_CXX_FLAGS='
if ($LASTEXITCODE -ne 0) { throw 'Reference configure failed' }
& $cmake --build $output --target llama-server -j 4
if ($LASTEXITCODE -ne 0) { throw 'Reference build failed' }
Get-FileHash (Join-Path $output 'bin/llama-server') -Algorithm SHA256
