[CmdletBinding()]
param([string]$Workspace='C:\APK-Workbench')
$ErrorActionPreference='Stop'
$source=$PSScriptRoot
$project=Split-Path $source -Parent
$build=Join-Path $project ('Outputs\build-'+[Guid]::NewGuid().ToString('N'))
$out=Join-Path $project 'Outputs\Release staging\0.2.60'
$tools=Join-Path $Workspace 'Toolchain\Android-Build-Tools'
$javaBin=Join-Path $Workspace 'Toolchain\Java\bin'
$platform=Join-Path $Workspace 'Toolchain\Android-SDK\platforms\android-35\android.jar'
function Invoke-Tool([string]$Program,[string[]]$Arguments){& $Program @Arguments;if($LASTEXITCODE -ne 0){throw "Build tool failed: $Program ($LASTEXITCODE)"}}
New-Item -ItemType Directory -Force -Path "$build\classes","$build\dex","$build\test",$out | Out-Null
$buildStamp=[TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow,[TimeZoneInfo]::FindSystemTimeZoneById('Eastern Standard Time')).ToString('yyyy-MM-dd')
[IO.File]::WriteAllText("$source\src\dev\odin\controls\BuildInfo.java", "package dev.odin.controls; final class BuildInfo { static final String BUILD_DATE = `"$buildStamp`"; }", [Text.UTF8Encoding]::new($false))
$clang=Join-Path $Workspace 'Toolchain\android-ndk-r30\toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe'
Invoke-Tool $clang @('--target=aarch64-linux-android30','-O2','-Wall','-Wextra','-Werror',"$source\native\ring-segments.c",'-o',"$source\assets\ring-segments")
Invoke-Tool $clang @('--target=aarch64-linux-android30','-O2','-Wall','-Wextra','-Werror',"$source\native\odin2-zones.c",'-o',"$source\assets\odin2-zones")
$sources=@(Get-ChildItem -LiteralPath "$source\src" -Filter '*.java' -Recurse | ForEach-Object FullName)
Invoke-Tool "$javaBin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-cp',$platform,'-d',"$build\classes")+$sources)
Invoke-Tool "$javaBin\javac.exe" @('-encoding','UTF-8','-d',"$build\test","$source\src\dev\odin\controls\FanCurve.java","$source\tests\FanCurveTest.java")
Invoke-Tool "$javaBin\java.exe" @('-cp',"$build\test",'dev.odin.controls.FanCurveTest')
Invoke-Tool "$javaBin\javac.exe" @('-encoding','UTF-8','-d',"$build\test","$source\src\dev\odin\controls\FanCurve.java","$source\src\dev\odin\controls\CurvePoints.java","$source\tests\CurvePointsTest.java")
Invoke-Tool "$javaBin\java.exe" @('-cp',"$build\test",'dev.odin.controls.CurvePointsTest')
Invoke-Tool "$javaBin\javac.exe" @('-encoding','UTF-8','-d',"$build\test","$source\src\dev\odin\controls\AppearanceColors.java","$source\tests\AppearanceColorsTest.java")
Invoke-Tool "$javaBin\java.exe" @('-cp',"$build\test",'dev.odin.controls.AppearanceColorsTest')
Invoke-Tool "$javaBin\javac.exe" @('-encoding','UTF-8','-d',"$build\test","$source\src\dev\odin\controls\PerformanceProfile.java","$source\tests\PerformanceProfileTest.java")
Invoke-Tool "$javaBin\java.exe" @('-cp',"$build\test",'dev.odin.controls.PerformanceProfileTest')
Invoke-Tool "$javaBin\javac.exe" @('-encoding','UTF-8','-d',"$build\test","$source\src\dev\odin\controls\SegmentColors.java","$source\tests\SegmentColorsTest.java")
Invoke-Tool "$javaBin\java.exe" @('-cp',"$build\test",'dev.odin.controls.SegmentColorsTest')
Invoke-Tool "$javaBin\javac.exe" @('-encoding','UTF-8','-d',"$build\test","$source\src\dev\odin\controls\ZoneFrame.java","$source\tests\ZoneFrameTest.java")
Invoke-Tool "$javaBin\java.exe" @('-cp',"$build\test",'dev.odin.controls.ZoneFrameTest')
Invoke-Tool "$javaBin\jar.exe" @('cf',"$build\classes.jar",'-C',"$build\classes",'.')
Invoke-Tool "$javaBin\java.exe" @('-cp',"$tools\lib\d8.jar",'com.android.tools.r8.D8','--min-api','30','--lib',$platform,'--output',"$build\dex","$build\classes.jar")
# Package shell assets with Unix line endings even from a Windows checkout.
$assets=Join-Path $build 'assets'
Copy-Item -LiteralPath "$source\assets" -Destination $assets -Recurse
Get-ChildItem -LiteralPath $assets -Filter '*.sh' -File -Recurse | ForEach-Object {
 $scriptText=[IO.File]::ReadAllText($_.FullName).Replace("`r`n","`n")
 [IO.File]::WriteAllText($_.FullName,$scriptText,[Text.UTF8Encoding]::new($false))
}
Invoke-Tool "$tools\aapt.exe" @('package','-f','-M',"$source\AndroidManifest.xml",'-S',"$source\res",'-A',$assets,'-I',$platform,'-F',"$build\unsigned.apk")
Push-Location "$build\dex"
try{Invoke-Tool "$tools\aapt.exe" @('add',"$build\unsigned.apk",'classes.dex')}finally{Pop-Location}
Invoke-Tool "$tools\zipalign.exe" @('-f','4',"$build\unsigned.apk","$build\aligned.apk")
$keyDir=Join-Path $Workspace 'Signing-Keys\Odin-Controls'
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
$key=Join-Path $keyDir 'odin-controls-local.jks'
# Preserve the existing signing identity so installed releases can update.
if(!(Test-Path -LiteralPath $key)){
 Invoke-Tool "$javaBin\keytool.exe" @('-genkeypair','-keystore',$key,'-alias','odin-controls','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Odin Controls Local Preview','-noprompt')
}
$apk=Join-Path $out 'Raven-System-Control-0.2.60.apk'
Invoke-Tool "$javaBin\java.exe" @('-jar',"$tools\lib\apksigner.jar",'sign','--ks',$key,'--ks-key-alias','odin-controls','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$build\aligned.apk")
Invoke-Tool "$javaBin\java.exe" @('-jar',"$tools\lib\apksigner.jar",'verify','--verbose',$apk)
Invoke-Tool "$tools\zipalign.exe" @('-c','4',$apk)
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  Raven-System-Control-0.2.60.apk" | Set-Content -LiteralPath "$out\SHA256SUMS.txt" -Encoding ascii
Write-Output "Built APK: $apk"


