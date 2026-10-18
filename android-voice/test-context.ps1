param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
# Requires the bounded device_context_retention.py TLS fixture, not the voice backend.
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$lines = & adb -s $Serial shell am instrument -w local.doja.voice/.ContextInstrumentation 2>&1
$exit = $LASTEXITCODE
$lines | Tee-Object -FilePath (Join-Path $output ('context-retention-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt'))
$text = $lines -join "`n"
if ($exit -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: passed=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: tls_resume=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: pending_human_retained=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: microphone_opened=false\s*$' -or
    $text -match 'Process crashed|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') {
    throw 'Context retention instrumentation failed; adb exit code alone is not success'
}
