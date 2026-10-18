param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
# First prepare tests/device_tls_shutdown.py on the Moto as UID 10218.
# Never uses the conversation/model coordinator or starts a capture service.
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$lines = & adb -s $Serial shell am instrument -w local.doja.voice/.ShutdownInstrumentation 2>&1
$exit = $LASTEXITCODE
$lines | Tee-Object -FilePath (Join-Path $output ('shutdown-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt'))
$text = $lines -join "`n"
if ($exit -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: passed=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: tls_close_worker=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: clock_after_silence=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: microphone_opened=false\s*$' -or
    $text -match 'Process crashed|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') {
    throw 'Shutdown regression failed; adb exit code alone is not success'
}
