$ErrorActionPreference = 'Stop'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\36.0.0'
$android = Join-Path $sdk 'platforms\android-36\android.jar'
$root = $PSScriptRoot
$output = Join-Path (Split-Path $root -Parent) 'artifacts\android-voice'
New-Item -ItemType Directory -Force -Path "$output\classes", "$output\dex" | Out-Null
$settings = Get-Content (Join-Path (Split-Path $root -Parent) 'config\assistant.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$nameLiteral = ConvertTo-Json -InputObject ([string]$settings.name) -Compress
$wakeLiteral = ConvertTo-Json -InputObject ([string]$settings.wake_word) -Compress
$wakeEnabled = ([bool]$settings.wake_enabled).ToString().ToLowerInvariant()
$conversation = Get-Content (Join-Path (Split-Path $root -Parent) 'config\conversation_voice.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$certificatePin = [string]$conversation.tls_certificate_sha256
if ($certificatePin -notmatch '^[0-9a-f]{64}$') { throw 'Missing conversation TLS certificate pin' }
$generated = Join-Path $output 'AssistantConfig.java'
$generatedSource = "package local.doja.voice; public final class AssistantConfig { public static final String NAME = $nameLiteral; public static final String WAKE_WORD = $wakeLiteral; public static final boolean WAKE_ENABLED = $wakeEnabled; public static final String CONVERSATION_CERT_SHA256 = `"$certificatePin`"; }"
[System.IO.File]::WriteAllText($generated, $generatedSource, (New-Object System.Text.UTF8Encoding($false)))
$sources = @(Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName)
$sources += $generated
& javac -encoding UTF-8 -source 8 -target 8 -classpath $android -d "$output\classes" @sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& jar cf "$output\classes.jar" -C "$output\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& "$tools\d8.bat" --min-api 29 --lib $android --output "$output\dex" "$output\classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& "$tools\aapt2.exe" link -I $android --manifest "$root\AndroidManifest.xml" -o "$output\unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
python -c "import zipfile; p=r'$output'; z=zipfile.ZipFile(p+r'\unsigned.apk','a'); z.write(p+r'\dex\classes.dex','classes.dex'); z.close()"
if ($LASTEXITCODE -ne 0) { throw 'dex packaging failed' }
& "$tools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
if (!(Test-Path "$output\pilot.keystore")) {
    & keytool -genkeypair -keystore "$output\pilot.keystore" -storepass android -keypass android -alias pilot -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Doja Local Pilot'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& "$tools\apksigner.bat" sign --ks "$output\pilot.keystore" --ks-pass pass:android --key-pass pass:android --out "$output\doja-voice-pilot.apk" "$output\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'signing failed' }
& "$tools\apksigner.bat" verify "$output\doja-voice-pilot.apk"
if ($LASTEXITCODE -ne 0) { throw 'signature verification failed' }
Write-Output "Built: $output\doja-voice-pilot.apk"
