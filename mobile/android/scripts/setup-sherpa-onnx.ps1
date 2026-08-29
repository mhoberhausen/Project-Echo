param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$sherpaVersion = '1.13.6'
$runtimeSha256 = 'BB7F891B259F4FAEE5C55D4CC10D79A3BA2B27416E9320D2C4D044400834D75B'
$segmentationSha256 = '24615EE884C897D9D2BA09BB4D30DA6BB1B15E685065962DB5B02E76E4996488'
$embeddingSha256 = '1A331345F04805BADBB495C775A6DDFFCDD1A732567D5EC8B3D5749E3C7A5E4B'
$onnxRuntimeSha256 = 'DC5E4C172B1BE9E530C6A62AD8F1BE3E0A911CABDEE6195ABF28DAB72477E194'
$sherpaJniSha256 = 'BB7943F0B4655254E11176837C41E8A4127E06FBDE60DDF4FCE0E82839F1A206'
$segmentationModelSha256 = '220AD67CA923BEF2FA91F2390C786097BF305BCEB5E261D4AF67B38E938E1079'

$androidRoot = Split-Path -Parent $PSScriptRoot
$appRoot = Join-Path $androidRoot 'app'
$jniDestination = Join-Path $appRoot 'src\main\jniLibs\arm64-v8a'
$modelDestination = Join-Path $appRoot 'src\main\assets\models\diarization'
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("huh-sherpa-onnx-" + [guid]::NewGuid())

function Assert-Hash([string]$Path, [string]$Expected) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $Expected) {
        throw "SHA-256 mismatch for $Path. Expected $Expected, received $actual."
    }
}

function Download([string]$Uri, [string]$Destination, [string]$Hash) {
    Invoke-WebRequest -Uri $Uri -OutFile $Destination
    Assert-Hash -Path $Destination -Expected $Hash
}

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $jniDestination, $modelDestination | Out-Null

    $runtimeArchive = Join-Path $temporaryRoot 'runtime.tar.bz2'
    $segmentationArchive = Join-Path $temporaryRoot 'segmentation.tar.bz2'
    $embeddingModel = Join-Path $temporaryRoot 'embedding.onnx'
    $runtimeExtract = Join-Path $temporaryRoot 'runtime'
    $segmentationExtract = Join-Path $temporaryRoot 'segmentation'

    Download `
        -Uri "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-v$sherpaVersion-android.tar.bz2" `
        -Destination $runtimeArchive `
        -Hash $runtimeSha256
    Download `
        -Uri 'https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2' `
        -Destination $segmentationArchive `
        -Hash $segmentationSha256
    Download `
        -Uri 'https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx' `
        -Destination $embeddingModel `
        -Hash $embeddingSha256

    New-Item -ItemType Directory -Path $runtimeExtract, $segmentationExtract | Out-Null
    tar -xf $runtimeArchive -C $runtimeExtract
    if ($LASTEXITCODE -ne 0) { throw 'Could not extract the sherpa-onnx Android runtime.' }
    tar -xf $segmentationArchive -C $segmentationExtract
    if ($LASTEXITCODE -ne 0) { throw 'Could not extract the segmentation model.' }

    $install = @(
        @{
            Source = Join-Path $runtimeExtract 'jniLibs\arm64-v8a\libonnxruntime.so'
            Destination = Join-Path $jniDestination 'libonnxruntime.so'
            Hash = $onnxRuntimeSha256
        },
        @{
            Source = Join-Path $runtimeExtract 'jniLibs\arm64-v8a\libsherpa-onnx-jni.so'
            Destination = Join-Path $jniDestination 'libsherpa-onnx-jni.so'
            Hash = $sherpaJniSha256
        },
        @{
            Source = Join-Path $segmentationExtract 'sherpa-onnx-pyannote-segmentation-3-0\model.onnx'
            Destination = Join-Path $modelDestination 'segmentation.onnx'
            Hash = $segmentationModelSha256
        },
        @{
            Source = $embeddingModel
            Destination = Join-Path $modelDestination 'embedding.onnx'
            Hash = $embeddingSha256
        }
    )

    foreach ($item in $install) {
        if ((Test-Path -LiteralPath $item.Destination) -and -not $Force) {
            Assert-Hash -Path $item.Destination -Expected $item.Hash
            Write-Host "Keeping existing $($item.Destination)"
        } else {
            Copy-Item -LiteralPath $item.Source -Destination $item.Destination -Force
            Assert-Hash -Path $item.Destination -Expected $item.Hash
            Write-Host "Installed $($item.Destination)"
        }
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        [System.IO.Directory]::Delete(('\\?\' + $temporaryRoot), $true)
    }
}
