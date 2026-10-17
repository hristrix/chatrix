# Draw the Chatrix icon family from editable geometric shapes.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $white = '#FAFAFC'
    $purple = '#9425FA'
    $script:shapes = @()
    function Polygon($points, $fill) { $script:shapes += @{ kind='polygon'; points=$points; fill=$fill } }
    function Line($points, $stroke, $width=2) { $script:shapes += @{ kind='line'; points=$points; stroke=$stroke; width=$width } }
    function Ellipse($x,$y,$w,$h,$fill,$stroke='',$width=2) { $script:shapes += @{ kind='ellipse'; x=$x; y=$y; w=$w; h=$h; fill=$fill; stroke=$stroke; width=$width } }
    function Box($x,$y,$w,$h,$fill,$cut=1) {
        Polygon "$($x+$cut),$y $($x+$w-$cut),$y $($x+$w),$($y+$cut) $($x+$w),$($y+$h-$cut) $($x+$w-$cut),$($y+$h) $($x+$cut),$($y+$h) $x,$($y+$h-$cut) $x,$($y+$cut)" $fill
    }
    function Star($cx,$cy,$outer,$inner,$count,$fill) {
        $points = for($j=0;$j -lt $count*2;$j++) {
            $angle = -[Math]::PI/2 + $j*[Math]::PI/$count
            $radius = if($j%2 -eq 0){$outer}else{$inner}
            ([string]::Format([Globalization.CultureInfo]::InvariantCulture,'{0:F3},{1:F3}',($cx+[Math]::Cos($angle)*$radius),($cy+[Math]::Sin($angle)*$radius)))
        }
        Polygon ($points -join ' ') $fill
    }
    $definitions = [ordered]@{
        'gui/icon_settings' = {
            Polygon '9,2 15,2 16,5 19,5 22,10 20,12 22,15 19,20 16,19 15,22 9,22 8,19 5,20 2,15 4,12 2,9 5,4 8,5' $white
            Ellipse 7 7 10 10 $purple
            Ellipse 10 10 4 4 $white
        }
        'gui/icon_history' = {
            Polygon '3,3 18,3 20,5 20,15 18,17 9,17 4,21 4,17 2,15 2,5' $white
            Box 6 7 9 2 $purple 0
            Box 6 11 6 2 $purple 0
            Ellipse 12 12 11 11 $purple
            Line '17.5,14 17.5,18 20.5,18' $white 2
        }
        'gui/icon_back' = {
            Polygon '12,2 2,12 12,22 16,18 13,15 22,15 22,9 13,9 16,6' $white
            Polygon '11,6 5,12 11,18 11,14 20,14 20,10 11,10' $purple
        }
        'gui/icon_tab_world' = {
            Ellipse 2 2 20 20 $purple $white 2
            Ellipse 7 2 10 20 '' $white 2
            Line '3,8 21,8' $white 2
            Line '3,16 21,16' $white 2
        }
        'gui/icon_tab_messages' = {
            Polygon '9,8 21,8 23,10 23,20 21,22 20,22 20,24 15,22 9,22 7,20 7,10' $purple
            Polygon '3,2 16,2 18,4 18,15 16,17 9,17 3,21 3,17 1,15 1,4' $white
            Box 5 7 3 3 $purple 0.5
            Box 11 7 3 3 $purple 0.5
        }
        'gui/icon_tab_general' = {
            Box 2 4 20 3 $white 0.5
            Box 2 11 20 3 $white 0.5
            Box 2 18 20 3 $white 0.5
            Box 6 2 4 7 $purple 1
            Box 15 9 4 7 $purple 1
            Box 6 16 4 7 $purple 1
        }
        'gui/pin' = {
            Polygon '7,3 17,3 17,7 15,7 15,12 19,16 19,18 13,18 12,23 11,18 5,18 5,16 9,12 9,7 7,7' $white
            Box 6 2 12 5 $purple 1
        }
        'badge/host' = {
            Polygon '2,5 7,10 12,2 17,10 22,5 20,21 4,21' $white
            Polygon '5,11 8,14 12,8 16,14 19,11 18,17 6,17' $purple
            Box 5 19 14 2 $purple 0
        }
        'badge/mod' = {
            Polygon '12,1 22,5 21,15 18,19 12,23 6,19 3,15 2,5' $white
            Polygon '12,5 18,8 17,14 15,17 12,19 9,17 7,14 6,8' $purple
        }
        'badge/vip' = {
            Polygon '6,2 18,2 23,9 12,23 1,9' $white
            Polygon '7,5 17,5 20,9 12,19 4,9' $purple
            Line '4,9 20,9' $white 1.5
            Line '8,5 12,19 16,5' $white 1.5
        }
        'badge/sub' = {
            Star 12 12 11 5.5 5 $white
            Star 12 12 7 3.5 5 $purple
        }
        'platform/twitch' = {
            Polygon '4,2 22,2 22,15 16,21 11,21 7,24 7,21 2,21 2,5' $white
            Polygon '6,5 19,5 19,13 14,18 6,18' $purple
            Box 9 8 3 6 $white 0
            Box 14 8 3 6 $white 0
        }
        'platform/youtube' = {
            Box 1 4 22 16 $white 3
            Box 3 6 18 12 $purple 2
            Polygon '10,8 17,12 10,16' $white
        }
        'platform/kick' = {
            Box 3 2 5 20 $white 0
            Polygon '8,9 15,2 22,2 13,12 22,22 15,22 8,15' $purple
        }
        'platform/tiktok' = {
            Polygon '12,2 17,2 17,5 22,8 22,13 16,10 16,18 11,18' $purple
            Ellipse 3 14 13 9 $purple
            Polygon '10,1 14,1 14,4 19,7 19,10 13,7 13,17 9,17' $white
            Ellipse 2 13 11 8 $white
        }
        'particles/particle_texture' = {
            Star 12 12 11 4 4 $white
            Star 12 12 7 2.5 4 $purple
        }
    }
    function Points($text) {
        [Drawing.PointF[]]@($text.Split(' ') | ForEach-Object {
            $xy=$_.Split(',')
            New-Object Drawing.PointF([single]::Parse($xy[0],[Globalization.CultureInfo]::InvariantCulture),[single]::Parse($xy[1],[Globalization.CultureInfo]::InvariantCulture))
        })
    }
    function Draw-Shapes($graphics,$items) {
        foreach($shape in $items) {
            if($shape.kind -eq 'polygon') {
                $brush=New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml($shape.fill))
                try{$graphics.FillPolygon($brush,(Points $shape.points))}finally{$brush.Dispose()}
            } elseif($shape.kind -eq 'line') {
                $pen=New-Object Drawing.Pen([Drawing.ColorTranslator]::FromHtml($shape.stroke),[single]$shape.width)
                $pen.LineJoin=[Drawing.Drawing2D.LineJoin]::Miter
                try{$graphics.DrawLines($pen,(Points $shape.points))}finally{$pen.Dispose()}
            } else {
                if($shape.fill) {
                    $brush=New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml($shape.fill))
                    try{$graphics.FillEllipse($brush,[single]$shape.x,[single]$shape.y,[single]$shape.w,[single]$shape.h)}finally{$brush.Dispose()}
                }
                if($shape.stroke) {
                    $pen=New-Object Drawing.Pen([Drawing.ColorTranslator]::FromHtml($shape.stroke),[single]$shape.width)
                    try{$graphics.DrawEllipse($pen,[single]$shape.x,[single]$shape.y,[single]$shape.w,[single]$shape.h)}finally{$pen.Dispose()}
                }
            }
        }
    }
    $atlas=New-Object Drawing.Bitmap(1024,1024)
    $atlasGraphics=[Drawing.Graphics]::FromImage($atlas)
    $atlasGraphics.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $index=0
    foreach($entry in $definitions.GetEnumerator()) {
        $script:shapes=@()
        & $entry.Value
        $svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        foreach($shape in $script:shapes) {
            if($shape.kind -eq 'polygon') { $svg += '<polygon points="'+$shape.points+'" fill="'+$shape.fill+'"/>' }
            elseif($shape.kind -eq 'line') { $svg += '<polyline points="'+$shape.points+'" fill="none" stroke="'+$shape.stroke+'" stroke-width="'+$shape.width+'"/>' }
            else {
                $fill=if($shape.fill){$shape.fill}else{'none'}
                $svg += '<ellipse cx="'+($shape.x+$shape.w/2)+'" cy="'+($shape.y+$shape.h/2)+'" rx="'+($shape.w/2)+'" ry="'+($shape.h/2)+'" fill="'+$fill+'"'
                if($shape.stroke){$svg += ' stroke="'+$shape.stroke+'" stroke-width="'+$shape.width+'"'}
                $svg += '/>'
            }
        }
        $svg += '</svg>'
        $svgPath=Join-Path 'docs/branding/icons' ($entry.Key+'.svg')
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($svgPath)) -Force | Out-Null
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($svgPath),$svg,(New-Object Text.UTF8Encoding($false)))
        $target=Join-Path 'src/main/resources/assets/chatrix/textures' ($entry.Key+'.png')
        $existing=[Drawing.Image]::FromFile([IO.Path]::GetFullPath($target))
        $size=$existing.Size
        $existing.Dispose()
        $large=New-Object Drawing.Bitmap(192,192)
        $g=[Drawing.Graphics]::FromImage($large)
        $g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.ScaleTransform(8,8)
        Draw-Shapes $g $script:shapes
        $g.Dispose()
        $small=New-Object Drawing.Bitmap($size.Width,$size.Height)
        $g=[Drawing.Graphics]::FromImage($small)
        $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.DrawImage($large,0,0,$size.Width,$size.Height)
        $g.Dispose()
        $small.Save([IO.Path]::GetFullPath($target),[Drawing.Imaging.ImageFormat]::Png)
        $small.Dispose(); $large.Dispose()
        $state=$atlasGraphics.Save()
        $atlasGraphics.TranslateTransform(($index%4)*256+16,[Math]::Floor($index/4)*256+16)
        $atlasGraphics.ScaleTransform(224/24,224/24)
        Draw-Shapes $atlasGraphics $script:shapes
        $atlasGraphics.Restore($state)
        $index++
    }
    $atlasGraphics.Dispose()
    $atlas.Save([IO.Path]::GetFullPath('docs/branding/chatrix-icons.png'),[Drawing.Imaging.ImageFormat]::Png)
    $atlas.Dispose()
    Write-Output "Rendered $index Chatrix icons with matching SVG sources."
} finally { Pop-Location }
