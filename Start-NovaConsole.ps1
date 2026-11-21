#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$BackendPath,
    [string]$JarPath,
    [string]$Region,
    [string]$ModelId
)

$ErrorActionPreference = 'Stop'

if (-not $BackendPath) {
    $BackendPath = Join-Path $PSScriptRoot 'backend'
}

if ($env:JAVA_HOME) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
        throw 'java.exe fehlt. Setze JAVA_HOME auf ein JDK ab Version 21.'
    }
} else {
    $javaCommand = Get-Command 'java.exe' -CommandType Application -ErrorAction SilentlyContinue
    if (-not $javaCommand) {
        throw 'Java fehlt. Verwende ein JDK ab Version 21 und setze JAVA_HOME oder PATH.'
    }
    $java = $javaCommand.Source
}

if (-not $JarPath) {
    $backend = (Resolve-Path -LiteralPath $BackendPath).Path
    $maven = Join-Path $backend 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) {
        throw "Maven Wrapper fehlt unter '$backend'."
    }

    Push-Location -LiteralPath $backend
    try {
        & $maven '-Dmaven.test.skip=true' 'package'
        if ($LASTEXITCODE -ne 0) {
            throw "Maven-Build fehlgeschlagen (Exitcode $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }

    $target = Join-Path $backend 'target'
    $jars = @(Get-ChildItem -LiteralPath $target -File -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($jars.Count -ne 1) {
        throw 'Gib die ausfuehrbare Spring-Boot-JAR mit -JarPath an.'
    }
    $JarPath = $jars[0].FullName
}

$jar = (Resolve-Path -LiteralPath $JarPath).Path
$runDirectory = if (Test-Path -LiteralPath $BackendPath -PathType Container) {
    (Resolve-Path -LiteralPath $BackendPath).Path
} else {
    Split-Path -Parent $jar
}

Push-Location -LiteralPath $runDirectory
try {
    $javaArguments = @('-jar', $jar, '--checker.bedrock-console.enabled=true', '--llm.provider=nova')
    if ($Region) {
        $javaArguments += "--checker.bedrock-console.region=$Region"
    }
    if ($ModelId) {
        $javaArguments += "--checker.bedrock-console.model-id=$ModelId"
    }
    & $java @javaArguments
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $exitCode
