$ErrorActionPreference = "Stop"
$AppDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $AppDir
$Bt = Join-Path $Root "tools\bt\android-15"
$AndroidJar = Join-Path $Root "tools\plat\android-34\android.jar"
$Src = Join-Path $AppDir "src"
$Out = Join-Path $AppDir "build"
$Icon = Join-Path $AppDir "res\mipmap\ic_launcher.png"

if (-not (Test-Path (Join-Path $Bt "aapt2.exe"))) { throw "build-tools 不存在: $Bt（先运行工具链下载）" }
if (-not (Test-Path $AndroidJar)) { throw "android.jar 不存在: $AndroidJar" }

if (-not (Test-Path $Icon)) {
    Add-Type -AssemblyName System.Drawing
    $size = 192
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = "AntiAlias"
    $g.TextRenderingHint = "AntiAliasGridFit"
    $g.Clear([System.Drawing.Color]::Transparent)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $r = 44
    $path.AddArc(0, 0, 2*$r, 2*$r, 180, 90)
    $path.AddArc($size-2*$r-1, 0, 2*$r, 2*$r, 270, 90)
    $path.AddArc($size-2*$r-1, $size-2*$r-1, 2*$r, 2*$r, 0, 90)
    $path.AddArc(0, $size-2*$r-1, 2*$r, 2*$r, 90, 90)
    $path.CloseFigure()
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 36, 60, 92))
    $g.FillPath($brush, $path)
    $font = New-Object System.Drawing.Font("Microsoft YaHei", 86, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $white = [System.Drawing.Brushes]::White
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = "Center"
    $fmt.LineAlignment = "Center"
    $rect = New-Object System.Drawing.RectangleF(0, -6, $size, $size)
    $g.DrawString("签", $font, $white, $rect, $fmt)
    $g.Dispose()
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Icon) | Out-Null
    $bmp.Save($Icon, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "生成图标: $Icon"
}

New-Item -ItemType Directory -Force -Path $Out | Out-Null
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $Out "classes"), (Join-Path $Out "gen"), (Join-Path $Out "dex")

Write-Host "[1/6] aapt2 compile..."
& (Join-Path $Bt "aapt2.exe") compile --dir (Join-Path $AppDir "res") -o (Join-Path $Out "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败" }

Write-Host "[2/6] aapt2 link..."
$GenJava = Join-Path $Out "gen"
& (Join-Path $Bt "aapt2.exe") link -o (Join-Path $Out "base.apk") -I $AndroidJar --manifest (Join-Path $AppDir "AndroidManifest.xml") --java $GenJava --min-sdk-version 24 --target-sdk-version 34 --version-code 55 --version-name "1.8.9" (Join-Path $Out "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

Write-Host "[3/6] javac..."
$Classes = Join-Path $Out "classes"
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
$sources = @(Get-ChildItem -Path $Src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += (Get-ChildItem -Path $GenJava -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$javaArgs = @("-encoding","UTF-8","-source","8","-target","8","-Xlint:none","-nowarn","-bootclasspath",$AndroidJar,"-classpath",$GenJava,"-d",$Classes) + $sources
& javac @javaArgs
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Write-Host "[4/6] d8..."
$Dex = Join-Path $Out "dex"
New-Item -ItemType Directory -Force -Path $Dex | Out-Null
$classJar = Join-Path $Out "classes.jar"
Remove-Item -Force -ErrorAction SilentlyContinue $classJar
Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::CreateFromDirectory($Classes, $classJar)
if ($LASTEXITCODE -ne 0) { throw "jar class package failed" }
$d8Args = @("--release","--lib",$AndroidJar,"--min-api","24","--output",$Dex,$classJar)
& (Join-Path $Bt "d8.bat") @d8Args 2>&1
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }
if (-not (Test-Path (Join-Path $Dex "classes.dex"))) { throw "classes.dex 未生成" }

Write-Host "[5/6] 打包 classes.dex..."
Add-Type -AssemblyName System.IO.Compression.FileSystem
$baseApk = Join-Path $Out "base.apk"
$zip = [System.IO.Compression.ZipFile]::Open($baseApk, "Update")
try {
    $existing = $zip.GetEntry("classes.dex")
    if ($existing) { $existing.Delete() }
    $entry = $zip.CreateEntry("classes.dex")
    $fs = [System.IO.File]::OpenRead((Join-Path $Dex "classes.dex"))
    $es = $entry.Open()
    $fs.CopyTo($es)
    $fs.Dispose(); $es.Dispose()
} finally {
    $zip.Dispose()
}

Write-Host "[6/6] zipalign + 签名..."
$Aligned = Join-Path $Out "aligned.apk"
& (Join-Path $Bt "zipalign.exe") -f 4 $baseApk $Aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

$Ks = Join-Path $AppDir "appkey.jks"
$Keytool = $null
$candidates = @()
$cmdKeytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
if ($cmdKeytool) { $candidates += $cmdKeytool.Source }
$candidates += "C:\Program Files\Java\jdk-21.0.11\bin\keytool.exe"
$candidates += "C:\Program Files\Java\latest\bin\keytool.exe"
foreach ($cand in $candidates) {
    if ($cand -and (Test-Path $cand)) { $Keytool = $cand; break }
}
if (-not $Keytool) { throw "找不到 keytool.exe" }
if (-not (Test-Path $Ks)) {
    & $Keytool -genkeypair -v -keystore $Ks -alias heybox -keyalg RSA -keysize 2048 -validity 10950 `
        -storepass autosign2026 -keypass autosign2026 `
        -dname "CN=HeyboxAutoSign, OU=personal, O=personal, L=CN, ST=CN, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool 失败" }
}
$Final = Join-Path $Root "小黑盒自动签到.apk"
& (Join-Path $Bt "apksigner.bat") sign --ks $Ks --ks-pass pass:autosign2026 --ks-key-alias heybox --out $Final $Aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner 失败" }

& (Join-Path $Bt "apksigner.bat") verify --print-certs $Final
if ($LASTEXITCODE -eq 0) {
    $mb = [math]::Round((Get-Item $Final).Length / 1MB, 2)
    Write-Host ""
    Write-Host "构建成功: $Final (${mb} MB)"
}
