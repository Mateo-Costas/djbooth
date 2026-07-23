# Composes the booth GUI texture (booth.png, 1200x440) from a flat top-down CDJ image
# plus a placeholder mixer panel. Re-run whenever the source art changes.
#
# The CDJ source is re-framed so the device occupies the SAME fractional box the original
# art did (x 0.15..0.85, y 0.018..0.982 of a 1000x1000 canvas). That keeps every hotspot in
# BoothLayout.java aligned without touching a single coordinate.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools/compose_booth.ps1

Add-Type -AssemblyName System.Drawing

$srcCdj = 'C:\Users\Mateo\Downloads\CDJ-3000-top-hero.png'
$srcDjm = 'C:\Users\Mateo\Downloads\djm-900nxs2.png'
$out    = 'C:\Users\Mateo\djbooth\src\main\resources\assets\djbooth\textures\gui\booth.png'

# --- Canvas / region geometry (must match BoothLayout.java regions EXACTLY) ---
# Art is drawn into the SAME box the hotspots use (the region, inset 10px top/bottom),
# so device fractions equal region fractions and nothing sits "a bit high".
$TEX_W = 1200; $TEX_H = 440
$inset = 10; $regH = $TEX_H - 2 * $inset           # region top/height (y 10..430)
$deckAX = 10;  $deckBX = 770; $deckW = 420          # deck regions
$mixX   = 445; $mixW = 310                          # mixer region

# --- Framing box the original MAIN art used, on a 1000x1000 reference canvas ---
$refN = 1000
$devX = 150; $devY = 18; $devW = 700; $devH = 964 # device box inside the reference canvas

function Get-BBox($bmp) {
    $minx = $bmp.Width; $miny = $bmp.Height; $maxx = 0; $maxy = 0
    for ($y = 0; $y -lt $bmp.Height; $y += 2) {
        for ($x = 0; $x -lt $bmp.Width; $x += 2) {
            $c = $bmp.GetPixel($x, $y)
            $white = ($c.R -gt 245 -and $c.G -gt 245 -and $c.B -gt 245)
            if (-not $white -and $c.A -gt 10) {
                if ($x -lt $minx) { $minx = $x }; if ($x -gt $maxx) { $maxx = $x }
                if ($y -lt $miny) { $miny = $y }; if ($y -gt $maxy) { $maxy = $y }
            }
        }
    }
    return @($minx, $miny, ($maxx - $minx), ($maxy - $miny))
}

# 1. Load source and crop tight to the device (drop the white margins).
$src = [System.Drawing.Bitmap]::FromFile($srcCdj)
$bb = Get-BBox $src
$crop = New-Object System.Drawing.Bitmap($bb[2], $bb[3])
$gc = [System.Drawing.Graphics]::FromImage($crop)
$gc.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $bb[2], $bb[3])),
    $bb[0], $bb[1], $bb[2], $bb[3], [System.Drawing.GraphicsUnit]::Pixel)
$gc.Dispose(); $src.Dispose()

# 2. Re-frame onto a 1000x1000 reference canvas at the original device box.
$framed = New-Object System.Drawing.Bitmap($refN, $refN)
$gf = [System.Drawing.Graphics]::FromImage($framed)
$gf.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gf.DrawImage($crop, $devX, $devY, $devW, $devH)
$gf.Dispose(); $crop.Dispose()

# 3. Build the booth canvas.
$booth = New-Object System.Drawing.Bitmap($TEX_W, $TEX_H)
$g = [System.Drawing.Graphics]::FromImage($booth)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.Clear([System.Drawing.Color]::FromArgb(255, 26, 26, 30))

# Two decks: framed CDJ drawn into the deck REGION box (inset), not the full slot.
$g.DrawImage($framed, $deckAX, $inset, $deckW, $regH)
$g.DrawImage($framed, $deckBX, $inset, $deckW, $regH)
$framed.Dispose()

# Real DJM-900NXS2 mixer, drawn to fill the mixer REGION box (no re-framing; it has no
# white margin). Device fractions therefore equal region fractions for the mixer hotspots.
$djm = [System.Drawing.Bitmap]::FromFile($srcDjm)
$g.DrawImage($djm, $mixX, $inset, $mixW, $regH)
$djm.Dispose()

$g.Dispose()
$booth.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$booth.Dispose()
Write-Output "Wrote $out ($TEX_W x $TEX_H)"
