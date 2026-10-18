$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'src\local\doja\voice'
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\android-voice\policy-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& javac -encoding UTF-8 -d $output "$source\FaceState.java" "$source\UiPolicy.java" "$source\WeatherData.java" "$source\VoiceSessionPolicy.java" "$source\ConversationTurnPolicy.java" "$source\VoiceSocketCleanup.java" "$source\ConversationMemory.java" "$PSScriptRoot\tests\UiPolicyTest.java" "$PSScriptRoot\tests\VoiceSocketCleanupTest.java" "$PSScriptRoot\tests\ConversationMemoryTest.java"
if ($LASTEXITCODE -ne 0) { throw 'UI policy test compilation failed' }
& java -cp $output local.doja.voice.UiPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'UI policy tests failed' }
& java -cp $output local.doja.voice.VoiceSocketCleanupTest
if ($LASTEXITCODE -ne 0) { throw 'Socket cleanup tests failed' }
& java -cp $output local.doja.voice.ConversationMemoryTest
if ($LASTEXITCODE -ne 0) { throw 'Conversation RAM tests failed' }
