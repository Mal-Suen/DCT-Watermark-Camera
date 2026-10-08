# center-emulator.ps1 — 将 Android 模拟器窗口移回屏幕可见区。
# 定位方式：用 qemu-system-x86_64 进程的 MainWindowHandle，比标题匹配更稳（免端口号差异）。
# 用法： powershell -ExecutionPolicy Bypass -File center-emulator.ps1

Add-Type @'
using System;
using System.Runtime.InteropServices;
public class Win32State {
  [DllImport("user32.dll")]
  public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
  [DllImport("user32.dll")]
  public static extern bool MoveWindow(IntPtr hWnd, int x, int y, int w, int h, bool repaint);
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT { public int Left, Top, Right, Bottom; }
  [DllImport("user32.dll")]
  public static extern bool SetForegroundWindow(IntPtr hWnd);
}
'@

# 工作区（不含任务栏）
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class WorkArea {
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT { public int Left, Top, Right, Bottom; }
  [DllImport("user32.dll")]
  public static extern bool SystemParametersInfo(int uiAction, int uiParam, ref RECT pvParam, int fWinIni);
}
'@

$wa = New-Object WorkArea+RECT
[WorkArea]::SystemParametersInfo(0x0030, 0, [ref]$wa, 0) | Out-Null

$proc = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero }
if (-not $proc) {
  Write-Output "未找到模拟器窗口（qemu 可能未运行或窗口未创建）"
  exit 1
}

$hwnd = $proc.MainWindowHandle
$rect = New-Object Win32State+RECT
[Win32State]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top

# 屏幕内判断
$inside = ($rect.Left -ge $wa.Left) -and ($rect.Top -ge $wa.Top) -and ($rect.Left -lt $wa.Right) -and ($rect.Top -lt $wa.Bottom) -and ($w -gt 0 -and $h -gt 0)
if (-not $inside) {
  $newW = [Math]::Min($w, $wa.Right - $wa.Left - 40)
  $newH = [Math]::Min($h, $wa.Bottom - $wa.Top - 40)
  [Win32State]::MoveWindow($hwnd, $wa.Left + 25, $wa.Top + 25, $newW, $newH, $true) | Out-Null
  Write-Output "窗口移回可见区: 位置($($wa.Left+25),$($wa.Top+25)) 大小${newW}x${newH}"
} else {
  Write-Output "窗口已在屏幕内: 位置($($rect.Left),$($rect.Top)) 大小${w}x${h}"
}
[Win32State]::SetForegroundWindow($hwnd) | Out-Null