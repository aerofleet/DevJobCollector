$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_openai-lib.ps1"
function Get-OpenAIDocumentKey { return 'synthetic-test-key' }
$script:mode = 'success'
$script:requests = 0
function Invoke-RestMethod {
    param($Method, $Uri, $Headers, $ContentType, $Body, $TimeoutSec, $ErrorAction)
    $script:requests++
    $payload = [Text.Encoding]::UTF8.GetString($Body) | ConvertFrom-Json
    if ($Uri -ne 'https://api.openai.com/v1/responses' -or $payload.store -ne $false `
        -or $payload.max_output_tokens -ne 4096 -or $payload.input -ne '한글 테스트') { throw 'INVALID_REQUEST' }
    if ($script:mode -eq 'failure') { throw 'synthetic-test-key must never appear in errors' }
    $status = if ($script:mode -eq 'incomplete') { 'incomplete' } else { 'completed' }
    $content = switch ($script:mode) {
        'empty' { @() }
        'refusal' { @(@{type='refusal'; refusal='test'}) }
        default { @(@{type='output_text'; text='검증 완료'}) }
    }
    return @{status=$status;model='gpt-5-nano';usage=@{input_tokens=10;output_tokens=3};
        output=@(@{type='reasoning'},@{type='message';content=$content})}
}
if ((Invoke-OpenAIDocumentText -InputText '한글 테스트') -ne '검증 완료') { throw 'Output extraction failed' }
$passed = 1
foreach ($mode in @('failure','incomplete','empty','refusal')) {
    $script:mode = $mode
    $caught = $false
    try { Invoke-OpenAIDocumentText -InputText '한글 테스트' | Out-Null }
    catch {
        if ($_.Exception.Message -match 'synthetic-test-key') { throw 'Credential leaked' }
        $caught = $true
    }
    if (-not $caught) { throw "Did not reject $mode" }
    $passed++
}
$before = $script:requests
try { Invoke-OpenAIDocumentText -InputText ('x' * 120001) | Out-Null; throw 'Expected input rejection' }
catch { if ($_.Exception.Message -notmatch 'INPUT_TOO_LARGE') { throw } }
if ($script:requests -ne $before) { throw 'Oversize input reached API' }
$passed++
foreach ($file in @('_openai-lib.ps1','run-openai.ps1')) {
    $parseErrors = $null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $file), [ref]$null, [ref]$parseErrors)
    if ($parseErrors.Count) { throw "Parse failed: $file" }
}
Write-Output "PASS: $passed mock scenarios; 2 script syntax checks; no network calls."
