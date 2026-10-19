param(
    [string]$Serial = 'ZY227BCQ85',
    [ValidateSet('normal', 'cancel_inference', 'late_start', 'close', 'thanks', 'decline', 'yes', 'entry_new', 'entry_resume', 'entry_missing', 'timer_create', 'timer_yes', 'timer_missing', 'timer_query')][string]$Mode = 'normal',
    [switch]$InterruptFirst
)
$ErrorActionPreference = 'Stop'
# Backend must already be prepared with `start-andromeda-conversation.sh test 90`.
# This runner never prepares a microphone backend or starts VoiceService.
# Timer modes require `start-andromeda-conversation.sh test 90 timer` and use isolated storage.
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice'
New-Item -ItemType Directory -Force -Path $output | Out-Null
if ($InterruptFirst -and $Mode -notin @('normal', 'close', 'timer_create')) { throw 'Interruption control simulation supports normal, close or timer_create only' }
$interrupt = if ($InterruptFirst) { 'true' } else { 'false' }
$lines = & adb -s $Serial shell am instrument -w -e mode $Mode -e interrupt_first $interrupt local.doja.voice/.ConversationInstrumentation 2>&1
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
if ($InterruptFirst -and ($text -notmatch '(?m)^INSTRUMENTATION_RESULT: first_speech_interrupted=true\s*$' -or
        $text -notmatch '(?m)^INSTRUMENTATION_RESULT: unheard_answer_not_retained=true\s*$' -or
        $text -notmatch '(?m)^INSTRUMENTATION_RESULT: interrupt_asr_loaded_with_lfm=true\s*$' -or
        $text -notmatch '(?m)^INSTRUMENTATION_RESULT: interrupt_asr_released=true\s*$')) { throw 'Interruption was not verified' }
if ($Mode -eq 'entry_new' -and $text -notmatch '(?m)^INSTRUMENTATION_RESULT: entry_selection=new\s*$') { throw 'Normal entry reused old history' }
if ($Mode -eq 'entry_resume' -and $text -notmatch '(?m)^INSTRUMENTATION_RESULT: entry_selection=resume\s*$') { throw 'Recall entry did not restore verified candidate' }
if ($Mode -eq 'entry_missing' -and ($text -notmatch '(?m)^INSTRUMENTATION_RESULT: lookup_missing=true\s*$' -or $text -notmatch '(?m)^INSTRUMENTATION_RESULT: no_chat_invented=true\s*$')) { throw 'Missing entry invented a chat' }
if ($Mode.StartsWith('timer_')) {
    $expected = if ($Mode -eq 'timer_create') { 1 } else { 0 }
    if ($text -notmatch "(?m)^INSTRUMENTATION_RESULT: isolated_timers_created=$expected\s*$" -or
        $text -notmatch '(?m)^INSTRUMENTATION_RESULT: isolated_storage_cleaned=true\s*$' -or
        $text -notmatch '(?m)^INSTRUMENTATION_RESULT: real_timers_unchanged=true\s*$' -or
        $Mode -eq 'timer_create' -and ($text -notmatch '(?m)^INSTRUMENTATION_RESULT: android_creation_acknowledged=true\s*$' -or
            $text -notmatch '(?m)^INSTRUMENTATION_RESULT: isolated_timer_survived_model_shutdown=true\s*$' -or
            $text -notmatch '(?m)^INSTRUMENTATION_RESULT: direct_request_closed=true\s*$' -or
            $text -notmatch '(?m)^INSTRUMENTATION_RESULT: action_result_synthesized=true\s*$')) {
        throw 'Timer trial did not validate the actual isolated Android result'
    }
}
if ($Mode -in @('normal', 'close', 'thanks', 'decline', 'yes', 'entry_new', 'entry_resume', 'entry_missing') -and $text -notmatch '(?m)^INSTRUMENTATION_RESULT: one_wake_only=true\s*$') {
    throw 'Continuous conversation did not verify a single wake activation'
}
if ($Mode -in @('close', 'thanks', 'decline', 'yes', 'entry_new', 'entry_resume') -and ($text -notmatch '(?m)^INSTRUMENTATION_RESULT: farewell_synthesized=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: chat_closed=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: new_chat_isolated=true\s*$' -or
    $text -notmatch '(?m)^INSTRUMENTATION_RESULT: closed_chat_resumable=true\s*$')) {
    throw 'Chat close/isolation/resume checks failed'
}
