param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
$target = @()
if ($Serial) { $target = @('-s', $Serial) }
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\evaluations'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$report = Join-Path $output ('android-ui-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
$lines = & adb @target shell am instrument -w local.doja.voice/.FaceInstrumentation 2>&1
$code = $LASTEXITCODE
$lines | Tee-Object -FilePath $report
$text = $lines -join "`n"
# Android's am can exit 0 even when the instrumented process crashes.
if ($code -ne 0 -or $text -notmatch 'INSTRUMENTATION_RESULT: passed=true' -or
    $text -notmatch 'INSTRUMENTATION_CODE: -1' -or $text -match 'Process crashed|INSTRUMENTATION_FAILED') {
    throw "UI instrumentation failed. Report: $report"
}
