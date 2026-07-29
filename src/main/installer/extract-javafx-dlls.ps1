param(
    [Parameter(Mandatory=$true)]
    [string]$OutputDir
)

$base = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository\org\openjfx'
$version = '21.0.6'
$jars = @('javafx-graphics','javafx-media','javafx-web','javafx-swing')
$count = 0

Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($name in $jars) {
    $jarPath = Join-Path $base ($name + '\' + $version + '\' + $name + '-' + $version + '-win.jar')
    if (Test-Path $jarPath) {
        Write-Host "  $name..."
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
        foreach ($entry in $zip.Entries) {
            if ($entry.Name -like '*.dll') {
                $dest = Join-Path $OutputDir $entry.Name
                if (-not (Test-Path $dest)) {
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
                    $count++
                }
            }
        }
        $zip.Dispose()
    }
}

Write-Host "Extracted $count JavaFX DLLs to $OutputDir"