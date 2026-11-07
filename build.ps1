<#
.SYNOPSIS
    Nocturn Android 客户端编译脚本 (Release / Debug)

.DESCRIPTION
    自动配置环境（JDK 21、PATH），调用 Gradle 构建 APK，并在完成后展示产物路径与大小。
    支持自动安装至已连接的 ADB 设备或在资源管理器中打开产物目录。

.PARAMETER Type
    编译类型：'release' (默认), 'debug', 或 'all' (两者均编译)

.PARAMETER Clean
    在编译前执行 clean 清理操作

.PARAMETER Install
    编译完成后自动通过 adb 安装到已连接的 Android 设备 (优先安装 arm64-v8a 或 universal 包)

.PARAMETER Open
    编译完成后自动在 Windows 资源管理器中打开输出目录

.EXAMPLE
    .\build.ps1
    # 默认编译 release 版本

.EXAMPLE
    .\build.ps1 -Type debug
    # 编译 debug 版本

.EXAMPLE
    .\build.ps1 -Clean -Open
    # 清理并编译 release 版本，完成后打开产物目录

.EXAMPLE
    .\build.ps1 -Install
    # 编译 release 并自动安装到手机
#>

[CmdletBinding()]
param (
    [ValidateSet('release', 'debug', 'all', IgnoreCase = $true)]
    [string]$Type = 'release',

    [switch]$Clean,
    [switch]$Install,
    [switch]$Open
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Nocturn Android Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. 查找并配置 JDK 21 (AGENTS.md 要求 JDK 21)
function Get-Jdk21Dir {
    # 检查当前 JAVA_HOME 是否为 JDK 21
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        $ver = & (Join-Path $env:JAVA_HOME "bin\javac.exe") -version 2>&1
        if ($ver -match "\s21\.") {
            return $env:JAVA_HOME
        }
    }

    # 常见 JDK 21 安装位置检测
    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Amazon Corretto\jdk21*",
        "C:\Program Files\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-21*"
    )

    foreach ($pat in $candidates) {
        $matched = Get-Item $pat -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($matched -and (Test-Path (Join-Path $matched.FullName "bin\javac.exe"))) {
            return $matched.FullName
        }
    }
    return $null
}

$jdk21 = Get-Jdk21Dir
if ($jdk21) {
    $env:JAVA_HOME = $jdk21
    $env:PATH = "$jdk21\bin;$env:PATH"
    Write-Host "[✓] 使用 JDK 21: $jdk21" -ForegroundColor Green
} else {
    Write-Warning "[!] 未找到专用的 JDK 21，将使用当前系统默认 Java。"
}

# 2. 检查 Node.js
if (Get-Command "node" -ErrorAction SilentlyContinue) {
    $nodeVer = & node -v
    Write-Host "[✓] Node.js 版本: $nodeVer" -ForegroundColor Green
} else {
    Write-Warning "[!] 未在 PATH 中检测到 Node.js，如编译 Node 资产失败请确保已安装 Node 18+。"
}

# 3. 确定 Gradle 任务
$gradleTasks = @()
if ($Clean) {
    $gradleTasks += "clean"
}

switch ($Type.ToLower()) {
    'release' { $gradleTasks += ":app:assembleRelease" }
    'debug'   { $gradleTasks += ":app:assembleDebug" }
    'all'     { $gradleTasks += ":app:assembleDebug"; $gradleTasks += ":app:assembleRelease" }
}

Write-Host "`n[>] 目标任务: $($gradleTasks -join ' ')" -ForegroundColor Yellow
Write-Host "[>] 开始执行 Gradle 编译...`n" -ForegroundColor Yellow

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

# 4. 执行 Gradle 构建
$gradlew = Join-Path $ScriptDir "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Error "未找到 gradlew.bat，请确认脚本位于项目根目录！"
    exit 1
}

& $gradlew $gradleTasks
$exitCode = $LASTEXITCODE

$stopwatch.Stop()
$elapsed = $stopwatch.Elapsed

if ($exitCode -ne 0) {
    Write-Host "`n[✗] 编译失败，退出码: $exitCode (耗时: $($elapsed.ToString('mm\:ss')))" -ForegroundColor Red
    exit $exitCode
}

Write-Host "`n========================================" -ForegroundColor Green
Write-Host " [✓] 编译成功! 耗时: $($elapsed.ToString('mm\:ss'))" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# 5. 扫描并展示产物
$outputBase = Join-Path $ScriptDir "app\build\outputs\apk"
$targetTypes = if ($Type -eq 'all') { @('release', 'debug') } else { @($Type.ToLower()) }
$foundApks = @()

foreach ($t in $targetTypes) {
    $dir = Join-Path $outputBase $t
    if (Test-Path $dir) {
        $apks = Get-ChildItem -Path $dir -Filter "*.apk" | Sort-Object Length -Descending
        if ($apks) {
            Write-Host "`n【$($t.ToUpper()) APK 产物目录】: $dir" -ForegroundColor Cyan
            foreach ($apk in $apks) {
                $foundApks += $apk
                $sizeMb = [Math]::Round($apk.Length / 1MB, 2)
                Write-Host ("  - {0,-32} ({1,5} MB)" -f $apk.Name, $sizeMb) -ForegroundColor White
            }
        }
    }
}

# 6. 处理 -Install 选项
if ($Install) {
    Write-Host "`n[>] 正在检查已连接的 ADB 设备..." -ForegroundColor Yellow
    if (Get-Command "adb" -ErrorAction SilentlyContinue) {
        $devices = & adb devices | Select-String -Pattern "([^\s]+)\s+device$"
        if ($devices.Count -gt 0) {
            # 优先选择当前编译类型的 arm64-v8a 或 universal 包
            $apkToInstall = $foundApks | Where-Object { $_.Name -like "*arm64-v8a*" } | Select-Object -First 1
            if (-not $apkToInstall) {
                $apkToInstall = $foundApks | Where-Object { $_.Name -like "*universal*" } | Select-Object -First 1
            }
            if (-not $apkToInstall) {
                $apkToInstall = $foundApks | Select-Object -First 1
            }

            if ($apkToInstall) {
                Write-Host "[>] 正在安装 $($apkToInstall.Name) 到设备..." -ForegroundColor Cyan
                & adb install -r $apkToInstall.FullName
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "[✓] 安装成功！" -ForegroundColor Green
                } else {
                    Write-Warning "[!] ADB 安装返回非零状态码: $LASTEXITCODE"
                }
            } else {
                Write-Warning "[!] 未找到可安装的 APK 文件。"
            }
        } else {
            Write-Warning "[!] 未检测到已连接的 Android 设备，跳过安装。"
        }
    } else {
        Write-Warning "[!] 未找到 adb 命令，跳过安装。"
    }
}

# 7. 处理 -Open 选项
if ($Open) {
    $firstDir = Join-Path $outputBase ($targetTypes[0])
    if (Test-Path $firstDir) {
        Write-Host "`n[>] 正在打开输出目录: $firstDir" -ForegroundColor Cyan
        Start-Process "explorer.exe" -ArgumentList $firstDir
    }
}
