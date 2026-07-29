param(
    [Parameter(Mandatory=$true)]
    [string]$ImagesDir
)

$ErrorActionPreference = "Stop"

python tools/model/export_model.py --images-dir $ImagesDir
