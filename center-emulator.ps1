# center-emulator.ps1 - Move the Android emulator window back into the visible screen area.
# Locates the window via the qemu-system-x86_64 process MainWindowHandle (more stable
# than title matching - immune to port differences).
# Usage: powershell -ExecutionPolicy Bypass -File center-emulator.ps1
# Note: keep this file ASCII-only. A previous UTF-8/GBK mixed-encoding version silently
# broke the process filter (mojibake corrupted the script structure).

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

# Fix (2026-10-08 code review S7): the emulator spawns multiple qemu processes;
# take the first one that owns a window handle (the old version crashed with an
# IntPtr conversion error when $proc was an array).
$proc = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue |
  Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero } |
  Select-Object -First 1
if (-not $proc) {
  Write-Output "Emulator window not found (qemu not running or window not created yet)"
  exit 1
}

$hwnd = $proc.MainWindowHandle
$rect = New-Object Win32State+RECT
[Win32State]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top

$inside = ($rect.Left -ge $wa.Left) -and ($rect.Top -ge $wa.Top) -and ($rect.Left -lt $wa.Right) -and ($rect.Top -lt $wa.Bottom) -and ($w -gt 0 -and $h -gt 0)
if (-not $inside) {
  $newW = [Math]::Min($w, $wa.Right - $wa.Left - 40)
  $newH = [Math]::Min($h, $wa.Bottom - $wa.Top - 40)
  [Win32State]::MoveWindow($hwnd, $wa.Left + 25, $wa.Top + 25, $newW, $newH, $true) | Out-Null
  Write-Output "Window moved into view: pos($($wa.Left+25),$($wa.Top+25)) size ${newW}x${newH}"
} else {
  Write-Output "Window already on screen: pos($($rect.Left),$($rect.Top)) size ${w}x${h}"
}
[Win32State]::SetForegroundWindow($hwnd) | Out-Null
