param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
$services = & adb -s $Serial shell dumpsys activity services local.doja.voice
if ($LASTEXITCODE -ne 0 -or ($services -join "`n") -match 'ServiceRecord') { throw 'Inspect active audio before instrumentation; do not stop it for this test' }
$timers = & adb -s $Serial shell run-as local.doja.voice cat shared_prefs/timers.xml
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect pending timers' }
[xml]$prefs = $timers -join "`n"
if (@($prefs.map.ChildNodes | Where-Object { $_.NodeType -eq 'Element' }).Count -gt 0) { throw 'Pending timers: do not instrument over an existing human trial' }
$lines = & adb -s $Serial shell am instrument -w local.doja.voice/.SilenceInstrumentation 2>&1
$code = $LASTEXITCODE
$text = $lines -join "`n"
$lines | Tee-Object -FilePath (Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\silence-synthetic-device.txt')
foreach ($required in @('test=offline_android_silence', 'passed=true', 'microphone_opened=false', 'audio_played=false', 'llm_started=false', 'future_timers_unchanged=true', 'action_receipts_unchanged=true')) {
    if ($text -notmatch ('(?m)^INSTRUMENTATION_RESULT: ' + [regex]::Escape($required) + '\s*$')) { throw "Missing silence evidence: $required" }
}
if ($code -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or $text -match 'INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') {
    throw 'Offline Android silence test failed'
}
