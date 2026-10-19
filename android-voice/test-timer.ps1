param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
# Isolated preferences and random UUIDs; never starts VoiceService, TTS or ringing.
$lines = & adb -s $Serial shell am instrument -w local.doja.voice/.TimerAuthorizationInstrumentation 2>&1
$exit = $LASTEXITCODE
$text = $lines -join "`n"
$lines | Tee-Object -FilePath (Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\timer-authorization-device.txt')
foreach ($required in @('test=isolated_timer_authorization', 'passed=true', 'wire_rejections_passed=true',
    'real_timers_unchanged=true', 'microphone_opened=false', 'audio_played=false', 'audible_alarm_started=false', 'isolated_preferences=true')) {
    if ($text -notmatch ('(?m)^INSTRUMENTATION_RESULT: ' + [regex]::Escape($required) + '\s*$')) { throw "Missing timer evidence: $required" }
}
if ($exit -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or
    $text -match 'INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') {
    throw 'Isolated Android timer authorization failed'
}
