param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
$services = & adb -s $Serial shell dumpsys activity services local.doja.voice
if ($LASTEXITCODE -ne 0 -or ($services -join "`n") -match 'ServiceRecord') { throw 'Real service active: do not instrument' }
$raw = & adb -s $Serial shell run-as local.doja.voice cat shared_prefs/timers.xml
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect timers' }
[xml]$prefs = $raw -join "`n"
if (@($prefs.map.ChildNodes | Where-Object { $_.NodeType -eq 'Element' }).Count -ne 0) { throw 'Pending timers: do not instrument over human data' }
$lines = & adb -s $Serial shell am instrument -w local.doja.voice/.CaptureProbeInstrumentation 2>&1
$code = $LASTEXITCODE
$text = $lines -join "`n"
$lines | Tee-Object -FilePath (Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\capture-probe-synthetic-device.txt')
foreach ($required in @('test=quiet_capture_probe_no_microphone', 'passed=true', 'manual_start_gate=true', 'playback_start_gate=true', 'public_pcm_verified=true', 'microphone_opened=false', 'audio_played=false', 'llm_started=false', 'timers_unchanged=true', 'receipts_unchanged=true')) {
    if ($text -notmatch ('(?m)^INSTRUMENTATION_RESULT: ' + [regex]::Escape($required) + '\s*$')) { throw "Missing diagnostic evidence: $required" }
}
if ($code -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or $text -match 'INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') { throw 'Capture diagnostic synthetic/UI test failed' }
