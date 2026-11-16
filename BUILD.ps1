param(
    [Parameter(Mandatory)][string]$Instance,
    [string]$MinecraftRoot,
    [string]$JavaHome,
    [switch]$CoreOnly
)
$ErrorActionPreference='Stop'
$Instance=(Resolve-Path -LiteralPath $Instance).Path
$mc=if($MinecraftRoot){(Resolve-Path -LiteralPath $MinecraftRoot).Path}else{Split-Path (Split-Path $Instance)}
$runtime=Join-Path $mc 'runtime\windows-x64\java-runtime-epsilon'
$java=if($JavaHome){Join-Path $JavaHome 'bin'}elseif(Test-Path "$runtime\bin\javac.exe"){Join-Path $runtime 'bin'}else{Split-Path (Get-Command javac.exe -ErrorAction Stop).Source}
$CoreOnly=$CoreOnly -or !(Test-Path -LiteralPath "$PSScriptRoot\asset-patch" -PathType Container)
$outputName=if($CoreOnly){'xixi-interaction-0.6.1-core.jar'}else{'xixi-interaction-0.6.1-preview.jar'}
$build=Join-Path $PSScriptRoot ('build\'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $build 'classes'
$testClasses=Join-Path $build 'tests'
New-Item -ItemType Directory -Force -Path $classes,$testClasses | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$numen=Get-ChildItem -LiteralPath "$Instance\mods" -Filter 'numen-neoforge-*.jar' | Select-Object -First 1
if(!$numen){throw 'Numen mod not found'}
$archive=[IO.Compression.ZipFile]::OpenRead($numen.FullName)
try {
    $entry=$archive.Entries | Where-Object {$_.FullName -like 'META-INF/jarjar/*numen_api*.jar'} | Select-Object -First 1
    if(!$entry){throw 'Bundled Numen API not found'}
    $api=Join-Path $build 'numen-api.jar'
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$api)
} finally {$archive.Dispose()}
$versionName=Split-Path $Instance -Leaf
$version=Get-Content -Raw -LiteralPath "$Instance\$versionName.json" | ConvertFrom-Json
$gameArgs=@($version.arguments.game)
$neoVersion=$gameArgs[[array]::IndexOf($gameArgs,'--fml.neoForgeVersion')+1]
if($neoVersion -ne '26.1.2.126'){throw "This build targets NeoForge 26.1.2.126, got $neoVersion"}
$jars=@("$mc\libraries\net\neoforged\minecraft-client-patched\$neoVersion\minecraft-client-patched-$neoVersion.jar","$mc\libraries\net\neoforged\neoforge\$neoVersion\neoforge-$neoVersion-universal.jar")
foreach($library in $version.libraries){
    $allowed=(!$library.rules)
    foreach($rule in $library.rules){
        if($rule.os.name -and $rule.os.name -ne 'windows'){continue}
        if($rule.os.arch -and $rule.os.arch -notin @('x86_64','amd64')){continue}
        $allowed=($rule.action -eq 'allow')
    }
    if($allowed -and $library.downloads.artifact.path){$jars+=Join-Path "$mc\libraries" $library.downloads.artifact.path}
}
$jars+=@((Get-ChildItem -LiteralPath "$Instance\mods" -Filter '*.jar' | Where-Object {$_.Name -notlike 'xixi-interaction-*'}).FullName)+@($api)
foreach($path in $jars){if(!(Test-Path -LiteralPath $path)){throw "Missing dependency: $path"}}
$cp=$jars -join ';'
$sources=(Get-ChildItem -LiteralPath "$PSScriptRoot\src" -Recurse -Filter '*.java').FullName
$arguments=@('-proc:none','--release','21','-encoding','UTF-8','-cp',('"'+$cp.Replace('\','/')+'"'),'-d',('"'+$classes.Replace('\','/')+'"'))+@($sources | ForEach-Object {'"'+$_.Replace('\','/')+'"'})
$argFile=Join-Path $build 'compile.args'
[IO.File]::WriteAllLines($argFile,$arguments,[Text.UTF8Encoding]::new($false))
& "$java\javac.exe" "@$argFile"; if($LASTEXITCODE){throw 'Compilation failed'}
& "$java\javac.exe" -proc:none -encoding UTF-8 -cp "$classes;$cp" -d $testClasses "$PSScriptRoot\tests\RulesTest.java" "$PSScriptRoot\tests\BytecodeTest.java"
if($LASTEXITCODE){throw 'Test compilation failed'}
& "$java\java.exe" -cp "$testClasses;$classes" RulesTest; if($LASTEXITCODE){throw 'Rules tests failed'}
& "$java\java.exe" -cp "$testClasses;$cp" BytecodeTest; if($LASTEXITCODE){throw 'Mixin targets do not match'}
if(!$CoreOnly){
    python "$PSScriptRoot\bake_ds_poses.py"; if($LASTEXITCODE){throw 'DS pose generation failed'}
    python "$PSScriptRoot\validate_assets.py"; if($LASTEXITCODE){throw 'Assets invalid'}
}
python "$PSScriptRoot\tests\test_installer.py"; if($LASTEXITCODE){throw 'Installer regression tests failed'}
$resources=Join-Path $build 'resources'
Copy-Item -LiteralPath "$PSScriptRoot\resources" -Destination $resources -Recurse
$license=Join-Path $PSScriptRoot 'LICENSE'
if(!(Test-Path -LiteralPath $license)){$license=Join-Path $PSScriptRoot 'share\LICENSE'}
if(Test-Path -LiteralPath $license){Copy-Item -LiteralPath $license -Destination "$resources\META-INF\LICENSE"}
$pose=Join-Path $resources 'assets\xixi_interaction\ds_poses.json'
if($CoreOnly -and (Test-Path -LiteralPath $pose)){Remove-Item -LiteralPath $pose}
& "$java\jar.exe" --create --file "$PSScriptRoot\$outputName" -C $classes . -C $resources .
if($LASTEXITCODE){throw 'Packaging failed'}
Get-FileHash -Algorithm SHA256 -LiteralPath "$PSScriptRoot\$outputName"
