param(
    [string]$Serial = 'ZY227BCQ85',
    [ValidateSet('store', 'ui', 'transport')][string]$Mode = 'store'
)
$ErrorActionPreference = 'Stop'
$package = & adb -s $Serial shell dumpsys package local.doja.voice
if ($LASTEXITCODE -ne 0 -or ($package -join "`n") -notmatch 'versionCode=18\b') { throw 'This numeric preflight requires the known code-18 APK; do not dump older UI' }
$services = & adb -s $Serial shell dumpsys activity services local.doja.voice
if ($LASTEXITCODE -ne 0 -or ($services -join "`n") -match 'ServiceRecord') { throw 'Human service active; do not instrument' }
$launch = & adb -s $Serial shell am start -W -n local.doja.voice/.MainActivity --ez offline_preview true
if ($LASTEXITCODE -ne 0 -or ($launch -join "`n") -match 'Error:|Exception') { throw 'Offline agenda preflight activity unavailable' }
$raw = & adb -s $Serial shell dumpsys activity local.doja.voice/.MainActivity agenda_counts
if ($LASTEXITCODE -ne 0) { throw 'Numeric agenda preflight unavailable' }
$line = @($raw | Where-Object { $_ -match '^agenda_counts=' })
if ($line.Count -ne 1) { throw 'No specific numeric agenda result; do not use a generic dump' }
$counts = ($line[0] -replace '^agenda_counts=', '') | ConvertFrom-Json
if ($counts.ALARM -ne 0 -or $counts.REMINDER -ne 0 -or $counts.delivery_active -ne $false) { throw 'Human agenda not empty; do not instrument' }
$rawTimers = & adb -s $Serial shell run-as local.doja.voice cat shared_prefs/timers.xml
if ($LASTEXITCODE -ne 0) { throw 'Timer preflight unavailable' }
[xml]$timers = $rawTimers -join "`n"
if (@($timers.map.ChildNodes | Where-Object { $_.NodeType -eq 'Element' }).Count -ne 0) { throw 'Human timer pending; do not instrument' }
$component = switch ($Mode) {
    'store' { 'AgendaInstrumentation' }
    'ui' { 'AgendaUiInstrumentation' }
    'transport' { 'AgendaTransportInstrumentation' }
}
$evidence = Join-Path (Split-Path $PSScriptRoot -Parent) "artifacts\android-voice\agenda-$Mode-device-code18.txt"
if (Test-Path $evidence) { throw 'Existing device evidence: inspect rather than overwrite or rerun automatically' }
$result = & adb -s $Serial shell am instrument -w "local.doja.voice/.$component"
$code = $LASTEXITCODE
$result | Tee-Object -FilePath $evidence
$text = $result -join "`n"
foreach ($required in @('passed=true', 'real_scheduler=false', 'microphone_opened=false', 'audio_played=false', 'llm_started=false')) {
    if ($text -notmatch [regex]::Escape("INSTRUMENTATION_RESULT: $required")) { throw "Agenda contract failed: $required" }
}
if ($Mode -eq 'store' -and $text -notmatch 'INSTRUMENTATION_RESULT: android_sqlite=true') { throw 'Actual Android SQLite not verified' }
if ($Mode -in @('store', 'transport') -and $text -notmatch 'INSTRUMENTATION_RESULT: isolated_storage_cleaned=true') { throw 'Isolated database cleanup unconfirmed' }
if ($Mode -eq 'ui' -and ($text -notmatch 'INSTRUMENTATION_RESULT: separate_empty_lists=true' -or $text -notmatch 'INSTRUMENTATION_RESULT: timeout_closes_dialogs=true')) { throw 'Agenda UI not verified' }
if ($Mode -eq 'transport' -and ($text -notmatch 'INSTRUMENTATION_RESULT: pinned_tls=true' -or $text -notmatch 'INSTRUMENTATION_RESULT: cases=4' -or $text -notmatch 'INSTRUMENTATION_RESULT: wrong_source_rejected=true' -or $text -notmatch 'INSTRUMENTATION_RESULT: wrong_protocol_rejected=true')) { throw 'Pinned TLS/agenda channel not verified' }
if ($code -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or $text -match 'INSTRUMENTATION_FAILED|Process crashed') { throw 'Agenda instrumentation did not complete successfully' }
