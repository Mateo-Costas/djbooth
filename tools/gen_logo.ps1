# Generates the mod logo (djbooth_logo.png, 256x256) shown in the Mods list.
# Dark panel + the real CDJ jog art in a circle + "DJ BOOTH" wordmark.
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen_logo.ps1

Add-Type -AssemblyName System.Drawing

$srcCdj = 'C:\Users\Mateo\Downloads\CDJ-3000-top-hero.png'
$out    = 'C:\Users\Mateo\djbooth\src\main\resources\djbooth_logo.png'
$S = 256

$bmp = New-Object System.Drawing.Bitmap($S, $S)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Dark background.
$g.Clear([System.Drawing.Color]::FromArgb(255, 18, 18, 22))

# CDJ art clipped to a circle, upper area.
$src = [System.Drawing.Bitmap]::FromFile($srcCdj)
$side = [Math]::Min($src.Width, $src.Height)
$sx = [int](($src.Width - $side) / 2)
$sy = [int](($src.Height - $side) / 2)
$d = 150; $cx = ($S - $d) / 2; $cy = 26
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddEllipse($cx, $cy, $d, $d)
$g.SetClip($path)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle($cx, $cy, $d, $d)),
    $sx, $sy, $side, $side, [System.Drawing.GraphicsUnit]::Pixel)
$g.ResetClip()
$src.Dispose()

# Ring around the jog.
$pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 37, 224, 192), 3)
$g.DrawEllipse($pen, $cx, $cy, $d, $d)

# Wordmark.
$font = New-Object System.Drawing.Font('Arial Black', 30, [System.Drawing.FontStyle]::Bold)
$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = [System.Drawing.StringAlignment]::Center
$brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$g.DrawString('DJ BOOTH', $font, $brush, ($S / 2), 196, $sf)

$g.Dispose()
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "Wrote $out"
