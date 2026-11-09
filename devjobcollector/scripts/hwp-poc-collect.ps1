param([string]$OutputDirectory = "$env:TEMP\djc-hwp-corpus", [int]$Target = 30, [int]$Page = 0, [string]$Experience = '신입')
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$search = 'https://djc-api.itsdev.kr/api/v1/jobs/search?size=500&page=' + $Page
if ($Experience) { $search += '&experience=' + [uri]::EscapeDataString($Experience) }
$response = Invoke-RestMethod $search -TimeoutSec 20
$items = @()
$seen = [System.Collections.Generic.HashSet[string]]::new()
$jobs = @($response.content | Where-Object sourcePlatform -eq 'PUBLIC_ALIO' | Sort-Object @{Expression={ if ($_.id -eq 12303) { 0 } else { 1 } }})
foreach ($job in $jobs) {
    if ($items.Count -ge $Target) { break }
    try { $detail = Invoke-RestMethod "https://djc-api.itsdev.kr/api/v1/jobs/$($job.id)?_fresh=hwp-poc" -TimeoutSec 15 } catch { continue }
    foreach ($file in $detail.files) {
        if ($items.Count -ge $Target) { break }
        if ($file.fileName -notmatch '(?i)\.(hwp|hwpx)$' -or $file.fileUrl -notmatch '^https://opendata\.alio\.go\.kr/recruit/downloadAtchFile\?recrutAtchFileNo=(\d+)$') { continue }
        $number = $Matches[1]
        $destination = Join-Path $OutputDirectory "$($file.id)-$number.hwp"
        try {
            $r = Invoke-WebRequest "https://www.alio.go.kr/download/download.json?fileNo=$number" -TimeoutSec 20
            $bytes = $r.Content
            if ($bytes -isnot [byte[]] -or $bytes.Length -lt 8 -or $bytes.Length -gt 30MB) { continue }
            $magic = [BitConverter]::ToString($bytes[0..7])
            if (!$magic.StartsWith('D0-CF-11-E0-A1-B1-1A-E1') -and !$magic.StartsWith('50-4B-03-04')) { continue }
            $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
            if (!$seen.Add($sha)) { continue }
            [IO.File]::WriteAllBytes($destination, $bytes)
            $items += [pscustomobject]@{ jobId=$job.id; fileId=$file.id; name=$file.fileName; path=$destination; bytes=$bytes.Length; magic=$magic; sha256=$sha }
            Write-Host "Collected $($items.Count)/$Target"
        } catch { continue }
        Start-Sleep -Milliseconds 200
    }
}
$items | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $OutputDirectory 'manifest.json')
"Collected $($items.Count) files in $OutputDirectory"
