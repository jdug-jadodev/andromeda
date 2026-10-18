param(
    [string]$Serial = 'ZY227BCQ85',
    [ValidateSet('normal', 'cancel_inference', 'late_start', 'close', 'thanks', 'decline', 'yes')][string]$Mode = 'normal'
)
$ErrorActionPreference = 'Stop'
# Backend must already be prepared with `start-andromeda-conversation.sh test 90`.
# This runner never prepares a microphone backend or starts VoiceService.
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$lines = & adb -s $Serial shell am instrument -w -e mode $Mode local.doja.voice/.ConversationInstrumentation 2>&1
$exit = $LASTEXITCODE
$text = $lines -join "`n"
$lines | Tee-Object -FilePath (Join-Path $output "conversation-synthetic-$Mode-runner.txt")
if ($exit -ne 0 -or $text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$' -or
    $text -notmatch ('(?m)^INSTRUMENTATION_RESULT: test=synthetic_conversation_' + [regex]::Escape($Mode) + '\s*$') -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: microphone_opened=false\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: audio_played=false\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: wrong_source_rejected=true\s*$' -or
    $text -match 'INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_RESULT: (error|shortMsg)=') {
    throw 'Synthetic conversation failed; adb exit code alone is not success'
}
if ($Mode -in @('normal', 'close', 'thanks', 'decline', 'yes') -and $text -notmatch '(?m)^INSTRUMENTATION_RESULT: one_wake_only=true\s*$') {
    throw 'Continuous conversation did not verify a single wake activation'
}
if ($Mode -in @('close', 'thanks', 'decline', 'yes') -and ($text -notmatch '(?m)^INSTRUMENTATION_RESULT: farewell_synthesized=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: chat_closed=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: new_chat_isolated=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: closed_chat_resumable=true\s*$')) {
    throw 'Chat close/isolation/resume checks failed'
}
