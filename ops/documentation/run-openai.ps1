# Inputs arrive through environment variables, never interpolated into executable shell text.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_openai-lib.ps1"
$mode = $env:DOC_MODE
if ($mode -notin @('check', 'generate')) { throw 'INVALID_MODE' }
if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) { throw 'OPENAI_API_KEY secret is missing.' }
if ($mode -eq 'check') {
    $text = Invoke-OpenAIDocumentText -InputText 'Reply only OK.' -MaxOutputTokens 256 -Verbose
    if ($text.Trim().TrimEnd('.') -ne 'OK') { throw 'Unexpected connection check response.' }
    Write-Output 'OpenAI authentication and model request succeeded.'
    return
}
if ([string]::IsNullOrWhiteSpace($env:DOC_CONTEXT) -or [string]::IsNullOrWhiteSpace($env:DOC_TASK)) {
    throw 'Document context and task are required.'
}
$text = Invoke-OpenAIDocumentText -InputText "## Evidence`n$env:DOC_CONTEXT`n`n## Task`n$env:DOC_TASK" -Verbose
$directory = Join-Path $env:RUNNER_TEMP 'openai-document'
[void][IO.Directory]::CreateDirectory($directory)
[IO.File]::WriteAllText((Join-Path $directory 'report.md'), $text, [Text.UTF8Encoding]::new($false))
Write-Output 'Document generated. Download the openai-document artifact; content is not logged.'
