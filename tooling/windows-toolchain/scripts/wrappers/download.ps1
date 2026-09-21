Set-StrictMode -Version Latest

function Invoke-LazyBuilderDownload(
    [Parameter(Mandatory = $true)][string]$Uri,
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$MaxAttempts = 3
) {
    if ($MaxAttempts -lt 1) { throw 'MaxAttempts must be at least 1.' }

    $lastError = $null
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
        try {
            Write-Host "Downloading $Uri (attempt $attempt/$MaxAttempts)" -ForegroundColor Cyan
            Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $OutFile
            if (-not (Test-Path $OutFile)) {
                throw "Download completed without producing the expected file: $OutFile"
            }
            return
        }
        catch {
            $lastError = $_
            Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
            if ($attempt -ge $MaxAttempts) { break }

            # Small bounded linear backoff. CI should recover from transient CDN/proxy faults
            # without turning a network outage into a long hidden retry loop.
            Start-Sleep -Seconds $attempt
        }
    }

    throw "Failed to download $Uri after $MaxAttempts attempts. Last error: $($lastError.Exception.Message)"
}
