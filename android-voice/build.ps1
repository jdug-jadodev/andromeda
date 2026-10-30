param([ValidatePattern('^[a-z0-9-]+\.apk$')][string]$ApkName = 'doja-voice-pilot.apk', [string]$BuildDirectory = '')
$ErrorActionPreference = 'Stop'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\36.0.0'
$android = Join-Path $sdk 'platforms\android-36\android.jar'
$root = $PSScriptRoot
$output = Join-Path (Split-Path $root -Parent) 'artifacts\android-voice'
$shared = $output
$keystore = Join-Path $shared 'pilot.keystore'
if ($BuildDirectory) {
    $output = [System.IO.Path]::GetFullPath((Join-Path (Split-Path $root -Parent) $BuildDirectory))
    if (!$output.StartsWith($shared + '\', [System.StringComparison]::OrdinalIgnoreCase) -or (Test-Path $output)) { throw 'Isolated build must use a new artifact subdirectory' }
    if (!(Test-Path $keystore)) { throw 'Existing app signing key required for an update' }
}
New-Item -ItemType Directory -Force -Path "$output\classes", "$output\dex" | Out-Null
python (Join-Path $root 'prepare-speech-deps.py')
if ($LASTEXITCODE -ne 0) { throw 'Pinned Android ASR dependency check failed' }
python (Join-Path $root 'verify-probe-audio.py')
if ($LASTEXITCODE -ne 0) { throw 'Fixed public playback asset check failed' }
$probeAudio = Get-Content (Join-Path (Split-Path $root -Parent) 'config\capture_probe_audio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$speechLock = Get-Content (Join-Path (Split-Path $root -Parent) 'config\voice_interrupt_deps.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$speechJars = @($speechLock.dependencies | ForEach-Object { Join-Path $shared "speech-deps\$($_.name)\classes.jar" })
$settings = Get-Content (Join-Path (Split-Path $root -Parent) 'config\assistant.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$nameLiteral = ConvertTo-Json -InputObject ([string]$settings.name) -Compress
$wakeLiteral = ConvertTo-Json -InputObject ([string]$settings.wake_word) -Compress
$wakeEnabled = ([bool]$settings.wake_enabled).ToString().ToLowerInvariant()
$conversation = Get-Content (Join-Path (Split-Path $root -Parent) 'config\conversation_voice.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$certificatePin = [string]$conversation.tls_certificate_sha256
$guidedAgenda = ([bool]$conversation.guided_agenda).ToString().ToLowerInvariant()
$continuousVoice = ([bool]$conversation.continuous_voice).ToString().ToLowerInvariant()
$alarmLifecycle = ([bool]$conversation.alarm_lifecycle).ToString().ToLowerInvariant()
if ($certificatePin -notmatch '^[0-9a-f]{64}$') { throw 'Missing conversation TLS certificate pin' }
$generated = Join-Path $output 'AssistantConfig.java'
$generatedSource = "package local.doja.voice; public final class AssistantConfig { public static final String NAME = $nameLiteral; public static final String WAKE_WORD = $wakeLiteral; public static final boolean WAKE_ENABLED = $wakeEnabled; public static final boolean GUIDED_AGENDA = $guidedAgenda; public static final boolean CONTINUOUS_VOICE = $continuousVoice; public static final boolean ALARM_LIFECYCLE = $alarmLifecycle; public static final String CONVERSATION_CERT_SHA256 = `"$certificatePin`"; public static final String PROBE_AUDIO_SHA256 = `"$($probeAudio.sha256)`"; public static final int PROBE_AUDIO_BYTES = $($probeAudio.bytes); }"
[System.IO.File]::WriteAllText($generated, $generatedSource, (New-Object System.Text.UTF8Encoding($false)))
$sources = @(Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName)
$sources += $generated
$errorPreference = $ErrorActionPreference; $ErrorActionPreference = 'Continue'   # javac prints notes on stderr
& javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options -classpath (($android + ';') + ($speechJars -join ';')) -d "$output\classes" @sources
$ErrorActionPreference = $errorPreference
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& jar cf "$output\classes.jar" -C "$output\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& "$tools\d8.bat" --min-api 29 --lib $android --output "$output\dex" "$output\classes.jar" @speechJars
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& "$tools\aapt2.exe" link -I $android --manifest "$root\AndroidManifest.xml" -o "$output\unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
python -c "import zipfile; p=r'$output'; z=zipfile.ZipFile(p+r'\unsigned.apk','a'); z.write(p+r'\dex\classes.dex','classes.dex'); z.close()"
if ($LASTEXITCODE -ne 0) { throw 'dex packaging failed' }
$probePcm = Join-Path $shared 'public-playback\capture-probe-public.pcm'
python -c "import zipfile; z=zipfile.ZipFile(r'$output\unsigned.apk','a'); z.write(r'$probePcm','assets/capture-probe-public.pcm'); z.close()"
if ($LASTEXITCODE -ne 0) { throw 'Fixed public PCM packaging failed' }
foreach ($dependency in $speechLock.dependencies) {
    $native = Join-Path $shared "speech-deps\$($dependency.name)\jni\$($speechLock.abi)\$($dependency.native)"
    $entry = "lib/$($speechLock.abi)/$($dependency.native)"
    python -c "import zipfile; z=zipfile.ZipFile(r'$output\unsigned.apk','a'); z.write(r'$native',r'$entry'); z.close()"
    if ($LASTEXITCODE -ne 0) { throw 'Native ASR library packaging failed' }
}
& "$tools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
if (!(Test-Path $keystore)) {
    & keytool -genkeypair -keystore $keystore -storepass android -keypass android -alias pilot -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Doja Local Pilot'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& "$tools\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$output\$ApkName" "$output\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'signing failed' }
& "$tools\apksigner.bat" verify "$output\$ApkName"
if ($LASTEXITCODE -ne 0) { throw 'signature verification failed' }
Write-Output "Built: $output\$ApkName"
