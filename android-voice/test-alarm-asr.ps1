param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
$package = & adb -s $Serial shell dumpsys package local.doja.voice
if ($LASTEXITCODE -ne 0 -or ($package -join "`n") -notmatch 'versionCode=20\b') { throw 'This control requires the known code-20 APK' }
$services = & adb -s $Serial shell dumpsys activity services local.doja.voice
if ($LASTEXITCODE -ne 0 -or ($services -join "`n") -match 'ServiceRecord') { throw 'Human service active; do not synthesize or instrument' }
$launch = & adb -s $Serial shell am start -W -n local.doja.voice/.MainActivity --ez offline_preview true
if ($LASTEXITCODE -ne 0 -or ($launch -join "`n") -match 'Error:|Exception') { throw 'Offline agenda preflight unavailable' }
$counts = & adb -s $Serial shell dumpsys activity local.doja.voice/.MainActivity agenda_counts
if ($LASTEXITCODE -ne 0 -or ($counts -join "`n") -notmatch '(?m)^agenda_counts=\{"ALARM":0,"REMINDER":0,"delivery_active":false\}\s*$') { throw 'Human agenda not empty or unavailable' }
$rawTimers = & adb -s $Serial shell run-as local.doja.voice cat shared_prefs/timers.xml
if ($LASTEXITCODE -ne 0 -or (($rawTimers -join "`n") -split '<string ').Count -ne 1) { throw 'Human timers not empty' }
$evidence = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\alarm-relative-asr-device-code20.txt'
if (Test-Path $evidence) { throw 'Existing alarm ASR evidence: inspect instead of rerunning automatically' }
try {
    $fixture = & adb -s $Serial shell am instrument -w local.doja.voice/.FixtureInstrumentation
    $fixtureText = $fixture -join "`n"
    if ($LASTEXITCODE -ne 0 -or $fixtureText -notmatch 'INSTRUMENTATION_CODE: -1' -or
        $fixtureText -notmatch 'INSTRUMENTATION_RESULT: microphone_opened=false' -or
        $fixtureText -notmatch 'INSTRUMENTATION_RESULT: audio_played=false') { throw 'Public offline fixture generation failed' }
    $lines = & adb -s $Serial shell am instrument -w local.doja.voice/.AlarmAsrInstrumentation
    $exit = $LASTEXITCODE; $text = $lines -join "`n"; $lines | Tee-Object -FilePath $evidence
    foreach ($required in @('passed=true', 'real_vosk=true', 'real_lfm=true', 'pinned_tls=true',
            'isolated_alarm_created=true', 'android_receipt_acknowledged=true', 'direct_request_closed=true',
            'real_agenda_unchanged=true', 'real_timers_unchanged=true', 'isolated_storage_cleaned=true',
            'microphone_opened=false', 'audio_played=false', 'real_scheduler=false', 'real_alarm_sounded=false',
            'human_request_used=false')) {
        if ($text -notmatch ('(?m)^INSTRUMENTATION_RESULT: ' + [regex]::Escape($required) + '\s*$')) { throw "Alarm ASR contract failed: $required" }
    }
    if ($exit -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or $text -match 'INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') {
        throw 'Public alarm ASR instrumentation failed'
    }
} finally {
    & adb -s $Serial shell run-as local.doja.voice rm -f files/synthetic/andromeda_fixture_alarm_relative.wav | Out-Null
}
