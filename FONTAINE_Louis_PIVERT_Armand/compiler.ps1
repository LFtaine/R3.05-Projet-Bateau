$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = (Get-ChildItem projet/src/voyage/*.java).FullName
    $compilerVersion = (& javac -version 2>&1 | Out-String)
    if ($compilerVersion -match '^javac 1\.8\.') { $compat = @('-source','8','-target','8') }
    else { $compat = @('--release','8') }
    & javac -encoding UTF-8 @compat -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation impossible' }
    $tempBuild = (Resolve-Path build).Path
    & jar "-J-Djava.io.tmpdir=$tempBuild" cfe projet.jar voyage.Main -C build/classes .
    if ($LASTEXITCODE -ne 0) { throw 'Creation du JAR impossible' }
    Write-Host 'JAR cree : projet.jar'
} finally { Pop-Location }
