$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'src\local\doja\voice'
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\policy-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
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
& javac -encoding UTF-8 -d $output "$source\TimerRequestGuard.java" "$source\PhoneVolumePolicy.java" "$PSScriptRoot\tests\PhoneVolumePolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Phone volume compilation failed' }
& java -cp $output local.doja.voice.PhoneVolumePolicyTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\phone_volume_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Phone volume policy tests failed' }
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
& java -cp $output local.doja.voice.AgendaGuardTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\agenda_request_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Calendar literal/permit checks failed' }
& java -cp $output local.doja.voice.AgendaDialogueTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\agenda_dialogue_cases.tsv') (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\agenda_receipt_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Guided agenda/continuation policy tests failed' }
& java -cp $output local.doja.voice.AlarmLifecycleTest (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests\weekly_alarm_cases.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Weekly alarm/deletion/snooze policy tests failed' }
& javac -encoding UTF-8 -cp $output -d $output "$source\InterruptionAudioClock.java" "$source\SilenceRecognition.java" "$PSScriptRoot\tests\SilenceLifecycleTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Interruption lifecycle compilation failed' }
& java -cp $output local.doja.voice.SilenceLifecycleTest
if ($LASTEXITCODE -ne 0) { throw 'Interruption lifecycle/freshness tests failed' }
& javac -encoding UTF-8 -d $output "$source\SpeechDiagnostics.java" "$PSScriptRoot\tests\SpeechDiagnosticsTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Speech diagnostic compilation failed' }
& java -cp $output local.doja.voice.SpeechDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Speech completion/timing privacy tests failed' }
& javac -encoding UTF-8 -d $output "$source\WebAnswerPolicy.java" "$PSScriptRoot\tests\WebAnswerPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Web answer contract compilation failed' }
& java -cp $output local.doja.voice.WebAnswerPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Source-bound web answer contract tests failed' }
