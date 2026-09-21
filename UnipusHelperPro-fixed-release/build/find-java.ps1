# ============================================================
#  找出本机可用的 Java 25+，把 java.exe 路径输出到 stdout。
#
#  为什么要单独一个脚本：批处理里解析 `java -version` 太脆弱
#  （引号、空格路径、管道转义都会让它取不到版本号），
#  所以把探测逻辑放到 PowerShell 里做，只把结果交回 run.bat。
#
#  查找顺序：
#    1. 环境变量 JAVA_HOME
#    2. 注册表登记的 JDK / JRE（逐个实测版本）
#    3. PATH 里的 java
#  找到第一个主版本 >= 25 的就返回；一个都没有则输出空行。
# ============================================================

$ErrorActionPreference = 'SilentlyContinue'

$candidates = New-Object System.Collections.ArrayList

if ($env:JAVA_HOME) {
    [void]$candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
}

foreach ($root in @(
        'HKLM:\SOFTWARE\JavaSoft\JDK',
        'HKLM:\SOFTWARE\JavaSoft\Java Development Kit',
        'HKLM:\SOFTWARE\JavaSoft\Java Runtime Environment')) {
    Get-ChildItem $root -ErrorAction SilentlyContinue | ForEach-Object {
        $home_ = $_.JavaHome
        if (-not $home_) {
            $home_ = (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).JavaHome
        }
        if ($home_) {
            [void]$candidates.Add((Join-Path $home_ 'bin\java.exe'))
        }
    }
}

$onPath = Get-Command java -ErrorAction SilentlyContinue
if ($onPath) {
    [void]$candidates.Add($onPath.Source)
}

$best = $null
foreach ($exe in ($candidates | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath $exe)) { continue }
    # 注意：java -version 把版本号写在 stderr，而 $ErrorActionPreference='SilentlyContinue'
    # 会把 2>&1 合并进来的 stderr 记录直接丢掉，导致永远读不到版本号。
    # 所以这里临时把它设回 Continue，只为了能拿到 java -version 的输出。
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $first = (& $exe -version 2>&1 | Select-Object -First 1)
    $ErrorActionPreference = $saved
    $major = 0
    if ($first -match '"([0-9]+)') { $major = [int]$Matches[1] }
    if ($major -ge 25 -and -not $best) { $best = $exe }
}

if ($best) {
    try {
        $best = (New-Object -ComObject Scripting.FileSystemObject).GetFile($best).ShortPath
    }
    catch {
        # 短路径不可用时保持原路径
    }
    Write-Output $best
}
else {
    Write-Output ''
}
