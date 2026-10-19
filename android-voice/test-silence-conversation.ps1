param([string]$Serial = 'ZY227BCQ85')
$ErrorActionPreference = 'Stop'
# Fixed public speech and offline synthesis; simulate interruption control, NOT acoustic barge-in.
# No microphone, speaker, executable actions or real VoiceService.
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = (Get-Command adb).Source
$info.Arguments = "-s $Serial shell `"su -c '/data/local/doha-voice/start-andromeda-conversation.sh test 90'`""
$info.UseShellExecute = $false
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $info
$log = New-Object System.IO.StreamWriter((Join-Path $output "silence-conversation-backend-$stamp.txt"), $false, (New-Object System.Text.UTF8Encoding($false)))
$started = $false
try {
    $started = $process.Start()
    if (!$started) { throw 'Could not start bounded synthetic preparation' }
    $ready = $false
    while ($null -ne ($line = $process.StandardOutput.ReadLine())) {
        $log.WriteLine($line)
        if ($line -match '"event": "conversation_ready"') { $ready = $true; break }
    }
    if (!$ready) { throw 'Backend exited before readiness' }
    & (Join-Path $PSScriptRoot 'test-conversation.ps1') -Serial $Serial -Mode close -InterruptFirst
} finally {
    if ($started) {
        $log.Write($process.StandardOutput.ReadToEnd())
        $errors = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($errors) { $log.WriteLine($errors) }
    }
    $log.Dispose()
}
if ($process.ExitCode -ne 0) { throw 'Bounded synthetic backend failed; inspect timestamped evidence' }
