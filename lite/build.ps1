param([string]$SdkPath='', [string]$JdkPath='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=$PSScriptRoot
$repo=Split-Path $root -Parent
Push-Location -LiteralPath $root
try {
if(!$SdkPath){$SdkPath=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
if(!$JdkPath){$JdkPath=$env:JAVA_HOME}
if(!$JdkPath -or !(Test-Path -LiteralPath (Join-Path $JdkPath 'bin/javac.exe'))){throw 'Pass a JDK 17 path using -JdkPath'}
$env:JAVA_HOME=(Resolve-Path -LiteralPath $JdkPath).Path
$sdk=(Resolve-Path -LiteralPath $SdkPath).Path
$bt=(Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory | Where-Object {$_.Name -match '^\d+\.\d+\.\d+$'} | Sort-Object {[version]$_.Name} | Select-Object -Last 1).FullName
$android=Join-Path $sdk 'platforms/android-34/android.jar'
function Run([string]$tool,[string[]]$arguments){& $tool @arguments;if($LASTEXITCODE -ne 0){throw "Build failed: $tool"}}
$out=[IO.Path]::GetFullPath((Join-Path $root 'out'))
if($out -ne (Join-Path $root 'out')){throw 'Unexpected Lite output directory'}
if(Test-Path -LiteralPath $out){Remove-Item -LiteralPath $out -Recurse -Force}
New-Item -ItemType Directory -Path 'out/classes','out/gen','out/res/drawable','out/res/values','out/assets/licenses','out/assets/fonts','out/assets/symbols' -Force | Out-Null
Copy-Item -LiteralPath 'res/values/styles.xml' -Destination 'out/res/values/styles.xml'
# Whitelist the shared resources. Full Vela assets, settings, clients and media never enter this APK.
foreach($name in @('icon','banner','vela_mark')){Copy-Item -LiteralPath (Join-Path $repo "app/res/drawable/$name.xml") -Destination 'out/res/drawable'}
Get-ChildItem -LiteralPath (Join-Path $repo 'app/res/drawable') -Filter 'device_*.xml' | Copy-Item -Destination 'out/res/drawable'
foreach($name in @('device-icons.txt','device-icons.json','simple-icons-CC0.txt','simple-icons-DISCLAIMER.txt')){Copy-Item -LiteralPath (Join-Path $repo "app/assets/licenses/$name") -Destination 'out/assets/licenses'}
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination 'out/assets/licenses/LICENSE.txt'
Copy-Item -LiteralPath (Join-Path $repo 'app/assets/fonts/InterVariable.ttf') -Destination 'out/assets/fonts'
Copy-Item -LiteralPath (Join-Path $repo 'app/assets/fonts/OFL.txt') -Destination 'out/assets/licenses/Inter-OFL.txt'
Copy-Item -LiteralPath 'assets/channel-logos' -Destination 'out/assets' -Recurse
Copy-Item -LiteralPath 'assets/channel-logos/CREDITS.txt' -Destination 'out/assets/licenses/channel-logos.txt'
foreach($name in @('hdmi','live_tv','apps','settings')){Copy-Item -LiteralPath (Join-Path $repo "app/assets/symbols/$name.png") -Destination 'out/assets/symbols'}
Copy-Item -LiteralPath (Join-Path $repo 'app/assets/symbols/LICENSE-material-symbols.txt') -Destination 'out/assets/licenses'
Run (Join-Path $bt 'aapt2.exe') @('compile','--dir','out/res','-o','out/res.zip')
Run (Join-Path $bt 'aapt2.exe') @('link','-o','out/base.apk','-I',$android,'--manifest','AndroidManifest.xml','--java','out/gen','--custom-package','tv.vela.launcher','out/res.zip','--min-sdk-version','26','--target-sdk-version','33','-A','out/assets')
$sources=@(Get-ChildItem -LiteralPath 'src','out/gen' -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
foreach($name in @('TvInputs','DeviceProfiles','DeviceIcons','VelaTheme','TvTimelineView','UpNextView','TvRemotePress','TvPersonalization','TvChannelOrganizer')){$sources+=Join-Path $repo "app/src/tv/vela/launcher/$name.java"}
$utf8=New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines((Join-Path $out 'sources.txt'),@($sources | ForEach-Object {'"'+$_.Replace('\','/')+'"'}),$utf8)
Run (Join-Path $env:JAVA_HOME 'bin/javac.exe') @('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',($android+';'+(Join-Path $bt 'core-lambda-stubs.jar')),'-classpath',$android,'-d','out/classes','@out/sources.txt')
Run (Join-Path $env:JAVA_HOME 'bin/jar.exe') @('cf','out/classes.jar','-C','out/classes','.')
Run (Join-Path $bt 'd8.bat') @('--release','--min-api','26','--lib',$android,'--output','out','out/classes.jar')
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $out 'base.apk'),(Join-Path $out 'package'))
Copy-Item -LiteralPath 'out/classes.dex' -Destination 'out/package/classes.dex'
Run (Join-Path $env:JAVA_HOME 'bin/jar.exe') @('c0Mf','out/normalized.apk','-C','out/package','.')
Run (Join-Path $bt 'zipalign.exe') @('-f','-p','4','out/normalized.apk','out/aligned.apk')
$key=Join-Path $repo 'vela.keystore'
if(!(Test-Path -LiteralPath $key)){Run (Join-Path $env:JAVA_HOME 'bin/keytool.exe') @('-genkeypair','-keystore',$key,'-alias','vela','-keyalg','RSA','-keysize','2048','-validity','20000','-storepass','vela-local','-keypass','vela-local','-dname','CN=Vela')}
Run (Join-Path $bt 'apksigner.bat') @('sign','--ks',$key,'--ks-pass','pass:vela-local','--out','Vela-Lite.apk','out/aligned.apk')
Run (Join-Path $bt 'apksigner.bat') @('verify','--verbose','Vela-Lite.apk')
Write-Output 'Built Vela-Lite.apk (separate package; no full Vela runtime)'
} finally { Pop-Location }
