$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'src\local\doja\voice'
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\policy-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& javac -encoding UTF-8 -d $output "$source\LightColor.java" "$source\LightTarget.java" "$source\LightCommand.java" "$source\TimerRequestGuard.java" "$source\LightCommandParser.java" "$source\LotusLampProtocol.java" "$source\MrStarProtocol.java" "$source\LightCommandRouting.java" "$source\LightCommandExecutionPolicy.java" "$PSScriptRoot\tests\LightCommandTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Light command parser compilation failed' }
& java -cp $output local.doja.voice.LightCommandTest
if ($LASTEXITCODE -ne 0) { throw 'Light command parser tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\LotusLampProtocol.java" "$PSScriptRoot\tests\LotusLampProtocolTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Lotus protocol compilation failed' }
& java -cp $output local.doja.voice.LotusLampProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Lotus protocol tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\MrStarProtocol.java" "$PSScriptRoot\tests\MrStarProtocolTest.java"
if ($LASTEXITCODE -ne 0) { throw 'MR Star protocol compilation failed' }
& java -cp $output local.doja.voice.MrStarProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'MR Star protocol tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\LightCommandRouting.java" "$PSScriptRoot\tests\LightCommandRoutingTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Light routing compilation failed' }
& java -cp $output local.doja.voice.LightCommandRoutingTest
if ($LASTEXITCODE -ne 0) { throw 'Light routing tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\LightWriteAuthorization.java" "$source\LightCommandExecutionPolicy.java" "$PSScriptRoot\tests\LightWriteAuthorizationTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Light authorization compilation failed' }
& java -cp $output local.doja.voice.LightWriteAuthorizationTest
if ($LASTEXITCODE -ne 0) { throw 'Light authorization tests failed' }
& javac -encoding UTF-8 -d $output "$source\LightTarget.java" "$source\LightSchedule.java" "$PSScriptRoot\tests\LightScheduleTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Light schedule compilation failed' }
& java -cp $output local.doja.voice.LightScheduleTest
if ($LASTEXITCODE -ne 0) { throw 'Light schedule arithmetic tests failed' }
& javac -encoding UTF-8 -d $output "$source\LightTarget.java" "$source\LightSchedule.java" "$source\RestModeSchedule.java" "$source\ScreenBrightnessPolicy.java" "$PSScriptRoot\tests\RestModeScheduleTest.java" "$PSScriptRoot\tests\ScreenBrightnessPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Rest mode schedule compilation failed' }
& java -cp $output local.doja.voice.RestModeScheduleTest
if ($LASTEXITCODE -ne 0) { throw 'Rest mode schedule tests failed' }
& java -cp $output local.doja.voice.ScreenBrightnessPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Screen brightness policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\LightTarget.java" "$source\LightSchedule.java" "$source\LightScheduleVoiceCommand.java" "$source\TimerRequestGuard.java" "$source\LightScheduleVoiceParser.java" "$PSScriptRoot\tests\LightScheduleVoiceTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Light schedule voice compilation failed' }
& java -cp $output local.doja.voice.LightScheduleVoiceTest
if ($LASTEXITCODE -ne 0) { throw 'Light schedule voice parser tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\LightBleRetryPolicy.java" "$PSScriptRoot\tests\LightBleRetryPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'BLE retry policy compilation failed' }
& java -cp $output local.doja.voice.LightBleRetryPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'BLE retry policy tests failed' }
$voiceSource = Get-Content "$source\VoiceService.java" -Raw
foreach ($channel in @('agendaChannel','volumeChannel','lightChannel')) {
    if ($voiceSource -notmatch [regex]::Escape("$channel.spoken(")) { throw "Missing $channel.spoken wiring" }
}
if ($voiceSource -notmatch [regex]::Escape('"light_result".equals(')) { throw 'Missing light_result action completion wiring' }
Write-Output 'Voice channel wiring: agenda, volume and light confirmation/result callbacks present.'
& javac -encoding UTF-8 -d $output "$source\VoiceLifecyclePolicy.java" "$PSScriptRoot\tests\VoiceLifecyclePolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Voice lifecycle policy compilation failed' }
& java -cp $output local.doja.voice.VoiceLifecyclePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Voice lifecycle policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\VoiceStartPolicy.java" "$PSScriptRoot\tests\VoiceStartPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Voice startup policy compilation failed' }
& java -cp $output local.doja.voice.VoiceStartPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Voice startup rejection policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\VoiceHealthPolicy.java" "$PSScriptRoot\tests\VoiceHealthPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Voice health policy compilation failed' }
& java -cp $output local.doja.voice.VoiceHealthPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Voice health privacy policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\UnifiedVoicePolicy.java" "$PSScriptRoot\tests\UnifiedVoicePolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Unified voice compilation failed' }
& java -cp $output local.doja.voice.UnifiedVoicePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Unified voice policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\TimerRequestGuard.java" "$source\VolumeDialogue.java" "$source\PhoneVolumePolicy.java" "$PSScriptRoot\tests\PhoneVolumePolicyTest.java" "$PSScriptRoot\tests\VolumeDialogueTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Phone volume compilation failed' }
& java -cp $output local.doja.voice.PhoneVolumePolicyTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\phone_volume_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Phone volume policy tests failed' }
& java -cp $output local.doja.voice.VolumeDialogueTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\volume_dialogue_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Confirmed volume dialogue tests failed' }
& javac -encoding UTF-8 -d $output "$source\WebAnswerPolicy.java" "$source\FaceState.java" "$source\UiPolicy.java" "$source\WeatherData.java" "$source\VoiceSessionPolicy.java" "$source\ConversationTurnPolicy.java" "$source\VoiceSocketCleanup.java" "$source\ConversationMemory.java" "$source\ContinuousVoicePolicy.java" "$source\TimerRequestGuard.java" "$source\TimerActionPolicy.java" "$source\TimerCreation.java" "$source\SilenceWindowPolicy.java" "$source\SilenceDiagnostics.java" "$source\AudioInputGate.java" "$PSScriptRoot\tests\UiPolicyTest.java" "$PSScriptRoot\tests\VoiceSocketCleanupTest.java" "$PSScriptRoot\tests\ConversationMemoryTest.java" "$PSScriptRoot\tests\ContinuousVoicePolicyTest.java" "$PSScriptRoot\tests\TimerActionTest.java" "$PSScriptRoot\tests\SilencePolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'UI policy test compilation failed' }
& java -cp $output local.doja.voice.UiPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'UI policy tests failed' }
& java -cp $output local.doja.voice.VoiceSocketCleanupTest
if ($LASTEXITCODE -ne 0) { throw 'Socket cleanup tests failed' }
& java -cp $output local.doja.voice.ConversationMemoryTest
if ($LASTEXITCODE -ne 0) { throw 'Conversation RAM tests failed' }
& java -cp $output local.doja.voice.ContinuousVoicePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Continuous voice policy tests failed' }
& java -cp $output local.doja.voice.TimerActionTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\timer_request_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Timer authorization/transaction tests failed' }
& java -cp $output local.doja.voice.SilencePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Silence window/microphone ownership tests failed' }
& javac -encoding UTF-8 -d $output "$source\CaptureProbePolicy.java" "$source\CapturePlaybackPolicy.java" "$source\CaptureProbeAudio.java" "$source\CaptureProbeStats.java" "$PSScriptRoot\tests\CaptureProbeTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Capture diagnostic test compilation failed' }
$publicAudio = Get-Content (Join-Path (Split-Path $PSScriptRoot -Parent) 'config\capture_probe_audio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
& java -cp $output local.doja.voice.CaptureProbeTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\public-playback\capture-probe-public.pcm') $publicAudio.sha256 $publicAudio.bytes
if ($LASTEXITCODE -ne 0) { throw 'Capture diagnostic timing/numeric-privacy tests failed' }
& javac -encoding UTF-8 -d $output "$source\TimerRequestGuard.java" "$source\AlarmSchedule.java" "$source\AgendaRequestGuard.java" "$source\AgendaEntry.java" "$source\AgendaCancellation.java" "$source\AgendaOutputs.java" "$PSScriptRoot\tests\AgendaManagementTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Manual agenda management compilation failed' }
& java -cp $output local.doja.voice.AgendaManagementTest
if ($LASTEXITCODE -ne 0) { throw 'Manual agenda management tests failed' }
& javac -encoding UTF-8 -d $output "$source\TimerRequestGuard.java" "$source\AlarmSchedule.java" "$source\AgendaEntry.java" "$source\AgendaRequestGuard.java" "$source\AgendaDialogue.java" "$source\AgendaPermit.java" "$source\AgendaSpeechPolicy.java" "$source\ActionCompletionPolicy.java" "$source\AlarmDeletion.java" "$source\AlarmOccurrencePolicy.java" "$source\AlarmOccurrenceTransaction.java" "$PSScriptRoot\tests\AgendaGuardTest.java" "$PSScriptRoot\tests\AgendaDialogueTest.java" "$PSScriptRoot\tests\AlarmLifecycleTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Calendar guard compilation failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\ReminderListPolicy.java" "$PSScriptRoot\tests\ReminderListPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Reminder read policy compilation failed' }
& java -cp $output local.doja.voice.ReminderListPolicyTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\reminder_read_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Reminder read scope/local speech tests failed' }
& java -cp $output local.doja.voice.AgendaGuardTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\agenda_request_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Calendar literal/permit checks failed' }
& java -cp $output local.doja.voice.AgendaDialogueTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\agenda_dialogue_cases.tsv') (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\agenda_receipt_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Guided agenda/continuation policy tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$PSScriptRoot\tests\CalendarDatesTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Calendar date/hour compilation failed' }
& java -cp $output local.doja.voice.CalendarDatesTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\calendar_date_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Calendar date/hour independent validation tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$PSScriptRoot\tests\ReminderClarificationTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Reminder clarification compilation failed' }
& java -cp $output local.doja.voice.ReminderClarificationTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\reminder_clarification_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Reminder clarification human/receipt scope tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\ReminderDeletion.java" "$PSScriptRoot\tests\ReminderDeletionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Reminder deletion compilation failed' }
& java -cp $output local.doja.voice.ReminderDeletionTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\reminder_delete_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Reminder deletion target/authorization/fault tests failed' }
& java -cp $output local.doja.voice.AlarmLifecycleTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\weekly_alarm_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Weekly alarm/deletion/snooze policy tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\InterruptionAudioClock.java" "$source\SilenceRecognition.java" "$PSScriptRoot\tests\SilenceLifecycleTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Interruption lifecycle compilation failed' }
& java -cp $output local.doja.voice.SilenceLifecycleTest
if ($LASTEXITCODE -ne 0) { throw 'Interruption lifecycle/freshness tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\DecoderWarmup.java" "$PSScriptRoot\tests\DecoderWarmupTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Public decoder preparation compilation failed' }
& java -cp $output local.doja.voice.DecoderWarmupTest
if ($LASTEXITCODE -ne 0) { throw 'Public decoder preparation/privacy tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\InterruptionModelProfile.java" "$source\PublicSpeechPcm.java" "$PSScriptRoot\tests\StreamingDecoderTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Streaming decoder/public PCM compilation failed' }
& java -cp $output local.doja.voice.StreamingDecoderTest
if ($LASTEXITCODE -ne 0) { throw 'Streaming decoder/public PCM privacy tests failed' }
& javac -encoding UTF-8 -d $output "$source\SpeechDiagnostics.java" "$PSScriptRoot\tests\SpeechDiagnosticsTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Speech diagnostic compilation failed' }
& java -cp $output local.doja.voice.SpeechDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Speech completion/timing privacy tests failed' }
& javac -encoding UTF-8 -d $output "$source\SpeechPlaybackPolicy.java" "$PSScriptRoot\tests\SpeechPlaybackPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Speech playback policy compilation failed' }
& java -cp $output local.doja.voice.SpeechPlaybackPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Speech playback policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\WebAnswerPolicy.java" "$PSScriptRoot\tests\WebAnswerPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Web answer contract compilation failed' }
& java -cp $output local.doja.voice.WebAnswerPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Source-bound web answer contract tests failed' }
& javac -encoding UTF-8 -d $output "$source\FaceReaction.java" "$source\WeatherData.java" "$source\FaceReactionPolicy.java" "$source\GreetingPolicy.java" "$PSScriptRoot\tests\FaceReactionPolicyTest.java" "$PSScriptRoot\tests\GreetingPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Greeting/reaction policy compilation failed' }
& java -cp $output local.doja.voice.FaceReactionPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Face reaction policy tests failed' }
& java -cp $output local.doja.voice.GreetingPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Greeting period tests failed' }
& javac -encoding UTF-8 -d $output "$source\FaceState.java" "$source\ListeningSleepPolicy.java" "$source\UiPolicy.java" "$PSScriptRoot\tests\ListeningSleepPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Listening sleep policy compilation failed' }
& java -cp $output local.doja.voice.ListeningSleepPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Listening sleep policy tests failed' }
& javac -encoding UTF-8 -d $output "$source\FaceExitMotion.java" "$PSScriptRoot\tests\FaceExitMotionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Face exit motion compilation failed' }
& java -cp $output local.doja.voice.FaceExitMotionTest
if ($LASTEXITCODE -ne 0) { throw 'Face exit motion tests failed' }
