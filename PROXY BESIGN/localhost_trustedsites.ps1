# Crear una regla de rango para 127.0.0.1 apuntando a la Zona 2 (Sitios de confianza)
$path = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings\ZoneMap\Ranges\Range1"

if (!(Test-Path $path)) {
    New-Item -Path $path -Force | Out-Null
}

# 127.0.0.1 como dirección IP
Set-ItemProperty -Path $path -Name ":Range" -Value "127.0.0.1"
# http asignado a Zona 2 (Trusted Sites)
Set-ItemProperty -Path $path -Name "http" -Value 2 -Type DWord