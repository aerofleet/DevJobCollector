<#
.SYNOPSIS
공개 기업 채용 페이지의 수집 적합성을 진단합니다.

.EXAMPLE
./ops/tools/probe-company-page.ps1 -Url https://company.example/careers

.EXAMPLE
./ops/tools/probe-company-page.ps1 -InputFile ./candidates.csv -OutputCsv ./probe-result.csv

.EXAMPLE
./ops/tools/probe-company-page.ps1 -HtmlFile ./src/test/resources/fixtures/company-page-job-posting.html `
  -BaseUrl https://careers.example.com/jobs
#>
[CmdletBinding(DefaultParameterSetName = 'Url')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Url')]
    [string[]] $Url,

    [Parameter(Mandatory, ParameterSetName = 'Csv')]
    [string] $InputFile,

    [Parameter(Mandatory, ParameterSetName = 'File')]
    [string] $HtmlFile,

    [Parameter(Mandatory, ParameterSetName = 'File')]
    [string] $BaseUrl,

    [string] $OutputCsv,
    [ValidateRange(1, 60)]
    [int] $TimeoutSec = 15,
    [string] $UserAgent = 'DevJobCollector/1.0 (+https://itsdev.kr)'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-PrivateIpAddress {
    param([System.Net.IPAddress] $Address)

    if ([System.Net.IPAddress]::IsLoopback($Address) -or $Address.IsIPv6LinkLocal -or
        $Address.IsIPv6SiteLocal -or $Address.IsIPv6Multicast) {
        return $true
    }
    if ($Address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }

    $bytes = $Address.GetAddressBytes()
    return $bytes[0] -eq 10 -or
        $bytes[0] -eq 127 -or
        ($bytes[0] -eq 169 -and $bytes[1] -eq 254) -or
        ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168) -or
        $bytes[0] -eq 0
}

function ConvertTo-PublicUri {
    param([string] $Value)

    $parsed = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref] $parsed)) {
        throw "유효한 절대 URL이 아닙니다."
    }
    if ($parsed.Scheme -notin @('http', 'https') -or [string]::IsNullOrWhiteSpace($parsed.Host)) {
        throw "공개 HTTP(S) URL만 허용됩니다."
    }

    $normalizedHost = $parsed.Host.ToLowerInvariant()
    if ($normalizedHost -eq 'localhost' -or $normalizedHost.EndsWith('.localhost') -or
        $normalizedHost.EndsWith('.local')) {
        throw "로컬 네트워크 URL은 허용되지 않습니다."
    }

    $ipAddress = $null
    if ([System.Net.IPAddress]::TryParse($parsed.Host, [ref] $ipAddress) -and
        (Test-PrivateIpAddress -Address $ipAddress)) {
        throw "사설 또는 로컬 IP URL은 허용되지 않습니다."
    }
    return $parsed
}

function ConvertTo-RobotsRegex {
    param([string] $Pattern)

    $escaped = [regex]::Escape($Pattern).Replace('\*', '.*')
    if ($Pattern.EndsWith('$')) {
        return '^' + $escaped.Substring(0, $escaped.Length - 2) + '$'
    }
    return '^' + $escaped
}

function Test-RobotsPath {
    param(
        [string] $Content,
        [string] $PathAndQuery
    )

    $groups = [System.Collections.Generic.List[object]]::new()
    $agents = [System.Collections.Generic.List[string]]::new()
    $rules = [System.Collections.Generic.List[object]]::new()
    $hasRules = $false

    foreach ($rawLine in ($Content -split "`r?`n")) {
        $line = ($rawLine -replace '#.*$', '').Trim()
        if ($line -notmatch '^([^:]+):(.*)$') {
            continue
        }
        $name = $Matches[1].Trim().ToLowerInvariant()
        $value = $Matches[2].Trim()
        if ($name -eq 'user-agent') {
            if ($hasRules) {
                $groups.Add([pscustomobject]@{ Agents = @($agents); Rules = @($rules) })
                $agents.Clear()
                $rules.Clear()
                $hasRules = $false
            }
            $agents.Add($value.ToLowerInvariant())
            continue
        }
        if ($name -in @('allow', 'disallow') -and $agents.Count -gt 0) {
            $rules.Add([pscustomobject]@{ Kind = $name; Pattern = $value })
            $hasRules = $true
        }
    }
    if ($agents.Count -gt 0) {
        $groups.Add([pscustomobject]@{ Agents = @($agents); Rules = @($rules) })
    }

    $matchingRules = @($groups | Where-Object {
        $_.Agents -contains '*' -or $_.Agents -contains 'devjobcollector'
    } | ForEach-Object { $_.Rules })
    $matched = @($matchingRules | Where-Object {
        -not [string]::IsNullOrEmpty($_.Pattern) -and
        $PathAndQuery -match (ConvertTo-RobotsRegex -Pattern $_.Pattern)
    } | Sort-Object { $_.Pattern.Length } -Descending)

    if ($matched.Count -eq 0) {
        return [pscustomobject]@{ Allowed = $true; Rule = $null }
    }
    return [pscustomobject]@{
        Allowed = $matched[0].Kind -eq 'allow'
        Rule = "$($matched[0].Kind): $($matched[0].Pattern)"
    }
}

function Get-RobotsAssessment {
    param([System.Uri] $PageUri)

    $robotsUri = [System.Uri]::new($PageUri.GetLeftPart([System.UriPartial]::Authority) + '/robots.txt')
    try {
        $response = Invoke-WebRequest -Uri $robotsUri -Headers @{ 'User-Agent' = $UserAgent } `
            -TimeoutSec $TimeoutSec -MaximumRedirection 3 -SkipHttpErrorCheck
        $status = [int] $response.StatusCode
        if ($status -eq 404) {
            return [pscustomobject]@{ Url = $robotsUri; Status = 404; Known = $true; Allowed = $true; Rule = $null }
        }
        if ($status -in @(401, 403)) {
            return [pscustomobject]@{ Url = $robotsUri; Status = $status; Known = $true; Allowed = $false; Rule = 'robots access denied' }
        }
        if ($status -lt 200 -or $status -ge 300) {
            return [pscustomobject]@{ Url = $robotsUri; Status = $status; Known = $false; Allowed = $false; Rule = $null }
        }
        $assessment = Test-RobotsPath -Content $response.Content -PathAndQuery $PageUri.PathAndQuery
        return [pscustomobject]@{
            Url = $robotsUri
            Status = $status
            Known = $true
            Allowed = $assessment.Allowed
            Rule = $assessment.Rule
        }
    } catch {
        return [pscustomobject]@{ Url = $robotsUri; Status = $null; Known = $false; Allowed = $false; Rule = $null }
    }
}

function Test-JobPostingType {
    param($TypeValue)

    if ($null -eq $TypeValue) {
        return $false
    }
    return @($TypeValue) | Where-Object { "$_" -ieq 'JobPosting' } | Select-Object -First 1
}

function Find-JobPostingNodes {
    param($Node)

    $found = [System.Collections.Generic.List[object]]::new()
    if ($null -eq $Node -or $Node -is [string] -or $Node -is [ValueType]) {
        return @()
    }
    if ($Node -is [System.Collections.IEnumerable] -and $Node -isnot [pscustomobject]) {
        foreach ($item in $Node) {
            foreach ($job in (Find-JobPostingNodes -Node $item)) {
                $found.Add($job)
            }
        }
        return @($found)
    }

    $typeProperty = $Node.PSObject.Properties['@type']
    if ($null -ne $typeProperty -and (Test-JobPostingType -TypeValue $typeProperty.Value)) {
        $found.Add($Node)
        return @($found)
    }
    foreach ($property in $Node.PSObject.Properties) {
        foreach ($job in (Find-JobPostingNodes -Node $property.Value)) {
            $found.Add($job)
        }
    }
    return @($found)
}

function Test-PropertyValue {
    param($Node, [string] $Name)

    if ($null -eq $Node) {
        return $false
    }
    $property = $Node.PSObject.Properties[$Name]
    return $null -ne $property -and $null -ne $property.Value -and
        -not [string]::IsNullOrWhiteSpace("$($property.Value)")
}

function Measure-JsonLd {
    param([string] $Html)

    $pattern = '<script\b[^>]*type\s*=\s*["'']application/ld\+json["''][^>]*>(.*?)</script\s*>'
    $matches = [regex]::Matches($Html, $pattern,
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor
        [System.Text.RegularExpressions.RegexOptions]::Singleline)
    $jobs = [System.Collections.Generic.List[object]]::new()
    $malformed = 0
    foreach ($match in $matches) {
        try {
            $json = [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value).Trim()
            $node = $json | ConvertFrom-Json -Depth 100
            foreach ($job in (Find-JobPostingNodes -Node $node)) {
                $jobs.Add($job)
            }
        } catch {
            $malformed++
        }
    }

    $presentFields = 0
    foreach ($job in $jobs) {
        if (Test-PropertyValue -Node $job -Name 'title') { $presentFields++ }
        if ((Test-PropertyValue -Node $job -Name 'url') -or
            (Test-PropertyValue -Node $job -Name 'identifier')) { $presentFields++ }
        $organization = $job.PSObject.Properties['hiringOrganization']
        if ($null -ne $organization -and
            (Test-PropertyValue -Node $organization.Value -Name 'name')) { $presentFields++ }
    }
    $possibleFields = $jobs.Count * 3
    $completeness = if ($possibleFields -eq 0) { 0 } else {
        [math]::Round(($presentFields / $possibleFields) * 100, 1)
    }
    return [pscustomobject]@{
        ScriptCount = $matches.Count
        MalformedCount = $malformed
        JobPostingCount = $jobs.Count
        RequiredFieldCompleteness = $completeness
    }
}

function Get-PageSignals {
    param([string] $Html)

    $titleMatch = [regex]::Match($Html, '(?is)<title[^>]*>(.*?)</title>')
    $pageTitle = if ($titleMatch.Success) {
        [System.Net.WebUtility]::HtmlDecode($titleMatch.Groups[1].Value).Trim()
    } else { '' }
    $loginDetected = $Html -match '(?is)<input[^>]+type\s*=\s*["'']password["'']|로그인\s*(후|필요)|sign\s*in\s*required'
    $captchaDetected = $Html -match '(?i)captcha|cf-chl-|challenge-platform|hcaptcha|g-recaptcha'
    return [pscustomobject]@{
        LoginDetected = $loginDetected
        CaptchaDetected = $captchaDetected
        AccessGateDetected = $pageTitle -match '(?i)^\s*(로그인|sign\s*in|log\s*in|just a moment|access denied|forbidden)'
    }
}

function New-Report {
    param(
        [string] $CompanyName,
        [string] $TargetUrl,
        [Nullable[int]] $HttpStatus,
        [string] $ContentType,
        $Robots,
        $Measurement,
        $Signals,
        [string] $Verdict,
        [string] $Reason
    )

    return [pscustomobject]@{
        companyName = $CompanyName
        url = $TargetUrl
        checkedAt = [DateTimeOffset]::Now.ToString('o')
        httpStatus = $HttpStatus
        contentType = $ContentType
        robotsStatus = if ($null -eq $Robots) { $null } else { $Robots.Status }
        robotsAllowed = if ($null -eq $Robots) { $null } else { $Robots.Allowed }
        robotsRule = if ($null -eq $Robots) { $null } else { $Robots.Rule }
        loginDetected = if ($null -eq $Signals) { $null } else { $Signals.LoginDetected }
        captchaDetected = if ($null -eq $Signals) { $null } else { $Signals.CaptchaDetected }
        accessGateDetected = if ($null -eq $Signals) { $null } else { $Signals.AccessGateDetected }
        jsonLdScriptCount = if ($null -eq $Measurement) { 0 } else { $Measurement.ScriptCount }
        malformedJsonLdCount = if ($null -eq $Measurement) { 0 } else { $Measurement.MalformedCount }
        jobPostingCount = if ($null -eq $Measurement) { 0 } else { $Measurement.JobPostingCount }
        requiredFieldCompleteness = if ($null -eq $Measurement) { 0 } else { $Measurement.RequiredFieldCompleteness }
        termsReview = 'MANUAL_REQUIRED'
        verdict = $Verdict
        reason = $Reason
    }
}

function Test-CompanyPage {
    param([string] $CompanyName, [string] $TargetUrl)

    try {
        $pageUri = ConvertTo-PublicUri -Value $TargetUrl
    } catch {
        return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $null `
            -ContentType $null -Robots $null -Measurement $null -Signals $null `
            -Verdict 'BLOCKED' -Reason $_.Exception.Message
    }

    $robots = Get-RobotsAssessment -PageUri $pageUri
    if ($robots.Known -and -not $robots.Allowed) {
        return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $null `
            -ContentType $null -Robots $robots -Measurement $null -Signals $null `
            -Verdict 'BLOCKED' -Reason "robots.txt에서 대상 경로를 허용하지 않습니다."
    }

    try {
        $response = Invoke-WebRequest -Uri $pageUri -Headers @{ 'User-Agent' = $UserAgent } `
            -TimeoutSec $TimeoutSec -MaximumRedirection 5 -SkipHttpErrorCheck
        $status = [int] $response.StatusCode
        $contentType = "$($response.Headers.'Content-Type')"
        if ($status -lt 200 -or $status -ge 300) {
            return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
                -ContentType $contentType -Robots $robots -Measurement $null -Signals $null `
                -Verdict 'REVIEW_REQUIRED' -Reason "HTTP 상태가 성공 범위가 아닙니다."
        }

        $measurement = Measure-JsonLd -Html $response.Content
        $signals = Get-PageSignals -Html $response.Content
        if ($signals.AccessGateDetected) {
            return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
                -ContentType $contentType -Robots $robots -Measurement $measurement -Signals $signals `
                -Verdict 'BLOCKED' -Reason '로그인 또는 자동화 방지 게이트 화면이 감지되었습니다.'
        }
        if ($signals.LoginDetected -or $signals.CaptchaDetected) {
            return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
                -ContentType $contentType -Robots $robots -Measurement $measurement -Signals $signals `
                -Verdict 'REVIEW_REQUIRED' -Reason '로그인 또는 CAPTCHA 관련 정적 요소가 있어 수동 확인이 필요합니다.'
        }
        if (-not $robots.Known) {
            return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
                -ContentType $contentType -Robots $robots -Measurement $measurement -Signals $signals `
                -Verdict 'REVIEW_REQUIRED' -Reason 'robots.txt 상태를 확인하지 못했습니다.'
        }
        if ($measurement.JobPostingCount -gt 0 -and
            $measurement.RequiredFieldCompleteness -ge 95) {
            return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
                -ContentType $contentType -Robots $robots -Measurement $measurement -Signals $signals `
                -Verdict 'ACTIVE_CANDIDATE' -Reason 'JobPosting과 필수 필드가 확인되었습니다. 약관 수동 검토가 남았습니다.'
        }
        if ($measurement.JobPostingCount -gt 0) {
            return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
                -ContentType $contentType -Robots $robots -Measurement $measurement -Signals $signals `
                -Verdict 'REVIEW_REQUIRED' -Reason 'JobPosting 필수 필드 완전성이 95% 미만입니다.'
        }
        return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $status `
            -ContentType $contentType -Robots $robots -Measurement $measurement -Signals $signals `
            -Verdict 'SITEMAP_REQUIRED' -Reason '현재 페이지 HTML에 JobPosting이 없습니다.'
    } catch {
        return New-Report -CompanyName $CompanyName -TargetUrl $TargetUrl -HttpStatus $null `
            -ContentType $null -Robots $robots -Measurement $null -Signals $null `
            -Verdict 'REVIEW_REQUIRED' -Reason $_.Exception.Message
    }
}

if ($PSCmdlet.ParameterSetName -eq 'File') {
    $html = Get-Content -Raw -LiteralPath $HtmlFile
    $measurement = Measure-JsonLd -Html $html
    $signals = Get-PageSignals -Html $html
    $verdict = if ($measurement.JobPostingCount -gt 0 -and
        $measurement.RequiredFieldCompleteness -ge 95) { 'ACTIVE_CANDIDATE' } else { 'REVIEW_REQUIRED' }
    $reports = @(New-Report -CompanyName 'fixture' -TargetUrl $BaseUrl -HttpStatus 200 `
        -ContentType 'text/html' -Robots $null -Measurement $measurement -Signals $signals `
        -Verdict $verdict -Reason '오프라인 fixture 검사 결과입니다.')
} else {
    $targets = if ($PSCmdlet.ParameterSetName -eq 'Csv') {
        @(Import-Csv -LiteralPath $InputFile | ForEach-Object {
            if ([string]::IsNullOrWhiteSpace($_.url)) {
                throw "CSV에는 url 열이 필요합니다."
            }
            [pscustomobject]@{ CompanyName = $_.companyName; Url = $_.url }
        })
    } else {
        @($Url | ForEach-Object { [pscustomobject]@{ CompanyName = ''; Url = $_ } })
    }
    $reports = @($targets | ForEach-Object {
        Test-CompanyPage -CompanyName $_.CompanyName -TargetUrl $_.Url
    })
}

if (-not [string]::IsNullOrWhiteSpace($OutputCsv)) {
    $reports | Export-Csv -LiteralPath $OutputCsv -NoTypeInformation -Encoding utf8
}
$reports
