param(
    [Parameter(Mandatory = $true)]
    [string]$Repository,

    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,

    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Branch = 'launcher-update-channel'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "Updater manifest was not found: $ManifestPath"
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Launcher version must use MAJOR.MINOR.PATCH: $Version"
}
if ([string]::IsNullOrWhiteSpace($env:GH_TOKEN)) {
    throw 'GH_TOKEN is required to publish the Launcher update channel.'
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw
try {
    $manifestDocument = $manifest | ConvertFrom-Json
} catch {
    throw "Updater manifest is not valid JSON: $($_.Exception.Message)"
}
if ([string]$manifestDocument.version -ne $Version) {
    throw "Updater manifest version '$($manifestDocument.version)' does not match requested version '$Version'."
}

$content = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($manifest))
$refEndpoint = "repos/$Repository/git/ref/heads/$Branch"

$null = gh api $refEndpoint 2>$null
$branchExists = $LASTEXITCODE -eq 0
$global:LASTEXITCODE = 0

if (-not $branchExists) {
    # Bootstrap an orphan branch containing distribution metadata only. Never seed
    # this branch from main, otherwise source files become part of the channel.
    $blobPayload = @{ content = $content; encoding = 'base64' } | ConvertTo-Json -Compress
    $blobSha = $blobPayload | gh api --method POST "repos/$Repository/git/blobs" --input - --jq '.sha'
    if ([string]::IsNullOrWhiteSpace($blobSha)) { throw 'Could not create stable manifest blob.' }

    $treePayload = @{
        tree = @(@{
            path = 'stable/latest.json'
            mode = '100644'
            type = 'blob'
            sha = $blobSha
        })
    } | ConvertTo-Json -Depth 5 -Compress
    $treeSha = $treePayload | gh api --method POST "repos/$Repository/git/trees" --input - --jq '.sha'
    if ([string]::IsNullOrWhiteSpace($treeSha)) { throw 'Could not create updater channel tree.' }

    $commitPayload = @{
        message = 'release(launcher): bootstrap stable update channel'
        tree = $treeSha
        parents = @()
    } | ConvertTo-Json -Depth 4 -Compress
    $commitSha = $commitPayload | gh api --method POST "repos/$Repository/git/commits" --input - --jq '.sha'
    if ([string]::IsNullOrWhiteSpace($commitSha)) { throw 'Could not create updater channel commit.' }

    $refPayload = @{ ref = "refs/heads/$Branch"; sha = $commitSha } | ConvertTo-Json -Compress
    $refPayload | gh api --method POST "repos/$Repository/git/refs" --input - *> $null
} else {
    # Refuse to publish into a branch that has drifted into a source/distribution mix.
    $rootItems = @(gh api "repos/$Repository/contents?ref=$Branch" --jq '.[].name')
    $unexpected = @($rootItems | Where-Object { $_ -ne 'stable' })
    if ($unexpected.Count -gt 0) {
        throw "Updater channel contains unexpected root entries: $($unexpected -join ', ')"
    }

    $existingJson = gh api "repos/$Repository/contents/stable/latest.json?ref=$Branch" 2>$null
    $manifestExists = $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($existingJson)
    $global:LASTEXITCODE = 0

    $existing = $null
    if ($manifestExists) {
        $existingMetadata = $existingJson | ConvertFrom-Json
        $existing = [string]$existingMetadata.sha
        if ([string]::IsNullOrWhiteSpace($existing)) {
            throw 'Stable update channel metadata is missing the current latest.json SHA.'
        }

        $existingContent = ([string]$existingMetadata.content -replace '\s','')
        if ([string]::IsNullOrWhiteSpace($existingContent)) {
            throw 'Stable update channel metadata is missing current latest.json content.'
        }
        try {
            $existingManifestText = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($existingContent))
            $existingManifest = $existingManifestText | ConvertFrom-Json
            $existingVersion = [Version]([string]$existingManifest.version)
            $requestedVersion = [Version]$Version
        } catch {
            throw "Could not validate the currently published stable version: $($_.Exception.Message)"
        }
        if ($existingVersion -gt $requestedVersion) {
            throw "Refusing to downgrade stable Launcher channel from $existingVersion to $requestedVersion."
        }
    }

    $payload = @{
        message = "release(launcher): stable $Version"
        content = $content
        branch = $Branch
    }
    if ($manifestExists) { $payload.sha = $existing }
    $json = $payload | ConvertTo-Json -Compress
    $json | gh api --method PUT "repos/$Repository/contents/stable/latest.json" --input - *> $null
}

# Read back the exact committed bytes. Callers still run the canonical Python
# manifest validator afterward; this check ensures publication itself completed.
$published = gh api "repos/$Repository/contents/stable/latest.json?ref=$Branch" --jq '.content'
$published = ($published -replace '\s','')
if ([string]::IsNullOrWhiteSpace($published)) {
    throw 'Published stable updater manifest could not be read back.'
}

$publishedBytes = [Convert]::FromBase64String($published)
$expectedBytes = [Text.Encoding]::UTF8.GetBytes($manifest)
if ($publishedBytes.Length -ne $expectedBytes.Length) {
    throw 'Published stable updater manifest length does not match the requested manifest.'
}
for ($index = 0; $index -lt $expectedBytes.Length; $index++) {
    if ($publishedBytes[$index] -ne $expectedBytes[$index]) {
        throw 'Published stable updater manifest bytes do not match the requested manifest.'
    }
}

Write-Host "Launcher stable update channel published for v$Version on $Branch."
