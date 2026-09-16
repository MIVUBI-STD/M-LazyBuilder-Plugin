function Set-LazyBuilderGradleEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot
    )

    $LocalBase = if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $env:LOCALAPPDATA
    } else {
        [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    }
    if ([string]::IsNullOrWhiteSpace($LocalBase)) {
        throw 'LOCALAPPDATA is unavailable; LazyBuilder cannot create its process-local Gradle runtime directories.'
    }

    $LazyBuilderRoot = Join-Path $LocalBase 'LazyBuilder'
    $RuntimeTemp = Join-Path $LazyBuilderRoot 'temp'
    $GradleUserHome = Join-Path $LazyBuilderRoot 'build-tools\gradle-user-home'
    New-Item -ItemType Directory -Force -Path $RuntimeTemp | Out-Null
    New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null

    # Process-local only. Child Gradle/Java processes inherit these values, while
    # the user's global Windows TEMP/TMP configuration remains untouched.
    $env:TEMP = $RuntimeTemp
    $env:TMP = $RuntimeTemp
    $env:GRADLE_USER_HOME = $GradleUserHome

    # An inherited java.io.tmpdir must never win over the LazyBuilder path. Remove
    # inherited assignments, preserve unrelated GRADLE_OPTS, then append our
    # authoritative value last so a fresh DEV.cmd/PowerShell behaves identically.
    $ExistingGradleOpts = [string]$env:GRADLE_OPTS
    $WithoutTmpDir = [regex]::Replace(
        $ExistingGradleOpts,
        '(?i)(^|\s+)-Djava\.io\.tmpdir=(?:"[^"]*"|''[^'']*''|\S+)',
        ' '
    ).Trim()
    $TmpOption = "-Djava.io.tmpdir=`"$RuntimeTemp`""
    $env:GRADLE_OPTS = if ([string]::IsNullOrWhiteSpace($WithoutTmpDir)) {
        $TmpOption
    } else {
        "$WithoutTmpDir $TmpOption"
    }

    return [pscustomobject]@{
        Root = $LazyBuilderRoot
        Temp = $RuntimeTemp
        GradleUserHome = $GradleUserHome
        JavaTmpOption = $TmpOption
    }
}
