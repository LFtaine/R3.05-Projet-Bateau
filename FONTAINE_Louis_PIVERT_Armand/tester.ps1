param([switch]$Minute)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build/tests | Out-Null
    $versionCompilateur = (& javac -version 2>&1 | Out-String)
    if ($versionCompilateur -match '^javac 1\.8\.') { $compat = @('-source','8','-target','8') }
    else { $compat = @('--release','8','-Xlint:-options') }
    & javac -encoding UTF-8 @compat -cp projet.jar -d build/tests projet/test/voyage/TestsPont.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation des tests impossible' }
    $optionsTest = @()
    if ($Minute) { $optionsTest = @('--minute') }
    & java -cp 'projet.jar;build/tests' voyage.TestsPont @optionsTest
    if ($LASTEXITCODE -ne 0) { throw 'Tests en echec' }
} finally { Pop-Location }
