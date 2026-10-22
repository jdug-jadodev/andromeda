param([string]$BuildDirectory = 'artifacts\android-voice\mr-star-scanner-v1')
$ErrorActionPreference = 'Stop'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\36.0.0'
$android = Join-Path $sdk 'platforms\android-36\android.jar'
$root = $PSScriptRoot
$repo = Split-Path $root -Parent
$output = Join-Path $repo $BuildDirectory
$shared = Join-Path $repo 'artifacts\android-voice'
$keystore = Join-Path $shared 'pilot.keystore'
if (Test-Path $output) { throw "Build directory already exists: $output" }
New-Item -ItemType Directory -Force -Path "$output\classes", "$output\dex" | Out-Null
$source = Join-Path $root 'src'
$sources = @(Get-ChildItem $source -Recurse -Filter *.java | ForEach-Object FullName)
& javac -encoding UTF-8 -source 8 -target 8 -classpath $android -d "$output\classes" @sources
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner javac failed' }
& jar cf "$output\classes.jar" -C "$output\classes" .
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner jar failed' }
& "$tools\d8.bat" --min-api 29 --lib $android --output "$output\dex" "$output\classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner d8 failed' }
& "$tools\aapt2.exe" link -I $android --manifest (Join-Path $root 'AndroidManifest.xml') -o "$output\unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner aapt2 failed' }
python -c "import zipfile; p=r'$output'; z=zipfile.ZipFile(p+r'\unsigned.apk','a'); z.write(p+r'\dex\classes.dex','classes.dex'); z.close()"
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner dex packaging failed' }
& "$tools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner zipalign failed' }
if (!(Test-Path $keystore)) { throw 'Existing signing key required' }
& "$tools\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$output\mr-star-scanner.apk" "$output\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner signing failed' }
& "$tools\apksigner.bat" verify "$output\mr-star-scanner.apk"
if ($LASTEXITCODE -ne 0) { throw 'MR Star scanner signature verification failed' }
Write-Output "Built: $output\mr-star-scanner.apk"
