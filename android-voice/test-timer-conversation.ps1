param(
    [string]$Serial = 'ZY227BCQ85',
    [ValidateSet('timer_create', 'timer_yes', 'timer_missing', 'timer_query')][string]$Mode = 'timer_create',
    [switch]$InterruptFirst
)
$ErrorActionPreference = 'Stop'
# Only synthetic source. Public WAVs + real ASR/LFM/offline synthesis, no speaker.
# Creation uses isolated preferences/random timer IDs and cancels them on exit.
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$previous = Join-Path $output "conversation-synthetic-$Mode-runner.txt"
if (Test-Path $previous) { Copy-Item $previous (Join-Path $output "conversation-synthetic-$Mode-before-$stamp.txt") }
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = (Get-Command adb).Source
$info.Arguments = "-s $Serial shell `"su -c '/data/local/doha-voice/start-andromeda-conversation.sh test 90 timer'`""
$info.UseShellExecute = $false
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $info
$log = New-Object System.IO.StreamWriter((Join-Path $output "$Mode-backend-$stamp.txt"), $false, (New-Object System.Text.UTF8Encoding($false)))
$started = $false
try {
    $started = $process.Start()
    if (!$started) { throw 'Could not start the bounded synthetic preparation' }
    $ready = $false
    # Wait for the explicit readiness event, not a guessed sleep or a microphone.
    while ($null -ne ($line = $process.StandardOutput.ReadLine())) {
        $log.WriteLine($line)
        if ($line -match '"event": "conversation_ready"') { $ready = $true; break }
    }
    if (!$ready) { throw 'Backend exited before readiness' }
    & (Join-Path $PSScriptRoot 'test-conversation.ps1') -Serial $Serial -Mode $Mode -InterruptFirst:$InterruptFirst
} finally {
    if ($started) {
        $log.Write($process.StandardOutput.ReadToEnd())
        $errors = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($errors) { $log.WriteLine($errors) }
    }
    $log.Dispose()
}
if ($process.ExitCode -ne 0) { throw 'Bounded synthetic backend failed; inspect the timestamped evidence' }
