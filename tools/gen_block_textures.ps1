# Generates block textures for the CDJ and mixer blocks from the real top-down art.
# Top face = the device photo (centre-cropped to a square, 64x64); sides/bottom = dark brushed metal.
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen_block_textures.ps1

Add-Type -AssemblyName System.Drawing

$srcCdj = 'C:\Users\Mateo\Downloads\CDJ-3000-top-hero.png'
$srcDjm = 'C:\Users\Mateo\Downloads\djm-900nxs2.png'
$outDir = 'C:\Users\Mateo\djbooth\src\main\resources\assets\djbooth\textures\block'

function Save-TopFace($srcPath, $outPath, $size) {
    $src = [System.Drawing.Bitmap]::FromFile($srcPath)
    # Centre-crop to a square.
    $side = [Math]::Min($src.Width, $src.Height)
    $sx = [int](($src.Width - $side) / 2)
    $sy = [int](($src.Height - $side) / 2)
    $out = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $size, $size)),
        $sx, $sy, $side, $side, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $src.Dispose()
    $out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "Wrote $outPath"
}

function Save-Side($outPath, $size) {
    $out = New-Object System.Drawing.Bitmap($size, $size)
    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            # Dark brushed metal: faint vertical streaks + a lighter top lip.
            $base = 24
            $streak = [int](6 * [Math]::Sin($x * 1.3))
            $v = $base + $streak
            if ($y -le 1) { $v += 26 }          # top highlight
            if ($y -ge $size - 2) { $v -= 8 }   # bottom shade
            if ($v -lt 8) { $v = 8 }
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $v, $v, $v + 3))
        }
    }
    $out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "Wrote $outPath"
}

Save-TopFace $srcCdj (Join-Path $outDir 'cdj_top.png') 64
Save-TopFace $srcDjm (Join-Path $outDir 'mixer_top.png') 64
Save-Side (Join-Path $outDir 'cdj_side.png') 64
Save-Side (Join-Path $outDir 'mixer_side.png') 64
