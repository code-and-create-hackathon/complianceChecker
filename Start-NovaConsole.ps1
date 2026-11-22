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
        throw 'java.exe is missing. Set JAVA_HOME to a JDK 21 or newer.'
    }
} else {
    $javaCommand = Get-Command 'java.exe' -CommandType Application -ErrorAction SilentlyContinue
    if (-not $javaCommand) {
        throw 'Java is missing. Use a JDK 21 or newer and set JAVA_HOME or PATH.'
    }
    $java = $javaCommand.Source
}

if (-not $JarPath) {
    $backend = (Resolve-Path -LiteralPath $BackendPath).Path
    $maven = Join-Path $backend 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) {
        throw "Maven wrapper is missing in '$backend'."
    }

    Push-Location -LiteralPath $backend
    try {
        & $maven '-Dmaven.test.skip=true' 'package'
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed (exit code $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }

    $target = Join-Path $backend 'target'
    $jars = @(Get-ChildItem -LiteralPath $target -File -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($jars.Count -ne 1) {
        throw 'Pass the executable Spring Boot JAR with -JarPath.'
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
