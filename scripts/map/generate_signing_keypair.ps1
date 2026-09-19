<#
.SYNOPSIS
    Generates an Ed25519 keypair for offline map package manifest signing.
.DESCRIPTION
    Creates an Ed25519 private key and extracts the raw 32-byte public key.
    Outputs the Kotlin byteArray representation for embedding into MapCatalogConfig.kt.
    IMPORTANT: Keep the generated private key file (.key / .pem) outside the repository!
.PARAMETER OutputDir
    Directory to save the generated private and public key files.
#>
param(
    [string]$OutputDir = "$HOME\.resqmesh\keys"
)

$ErrorActionPreference = "Stop"

Write-Host "=== ResQMesh Offline Map Signing Keypair Generator ===" -ForegroundColor Cyan

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$privateKeyPath = Join-Path $OutputDir "map_signing_private_ed25519.pem"
$publicKeyDerPath = Join-Path $OutputDir "map_signing_public.der"

# 1. Check if OpenSSL is available
$hasOpenssl = (Get-Command openssl -ErrorAction SilentlyContinue) -ne $null

if ($hasOpenssl) {
    Write-Host "[1/3] Generating Ed25519 private key via OpenSSL..." -ForegroundColor Green
    openssl genpkey -algorithm Ed25519 -out $privateKeyPath
    Write-Host "Private key saved to: $privateKeyPath" -ForegroundColor Yellow

    Write-Host "[2/3] Extracting public key..." -ForegroundColor Green
    openssl pkey -in $privateKeyPath -pubout -outform DER -out $publicKeyDerPath
    $derBytes = [System.IO.File]::ReadAllBytes($publicKeyDerPath)
    # The last 32 bytes of a 44-byte Ed25519 SubjectPublicKeyInfo DER are the raw public key
    $rawPubBytes = $derBytes[($derBytes.Length - 32)..($derBytes.Length - 1)]
} else {
    Write-Host "[1/3] OpenSSL not found in PATH; using .NET / PowerShell fallback..." -ForegroundColor Yellow
    # Java or .NET fallback
    $javaCode = @"
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.io.FileOutputStream;

public class KeyGen {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        try (FileOutputStream fos = new FileOutputStream(args[0])) {
            fos.write(kp.getPrivate().getEncoded());
        }
        try (FileOutputStream fos = new FileOutputStream(args[1])) {
            fos.write(kp.getPublic().getEncoded());
        }
    }
}
"@
    $tempJava = Join-Path $env:TEMP "KeyGen.java"
    Set-Content -Path $tempJava -Value $javaCode
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($javac -and $java) {
        & javac $tempJava
        $classDir = [System.IO.Path]::GetDirectoryName($tempJava)
        & java -cp $classDir KeyGen $privateKeyPath $publicKeyDerPath
        $derBytes = [System.IO.File]::ReadAllBytes($publicKeyDerPath)
        $rawPubBytes = $derBytes[($derBytes.Length - 32)..($derBytes.Length - 1)]
    } else {
        throw "Neither OpenSSL nor JDK (javac/java) was found in PATH. Please install OpenSSL or JDK to generate keys."
    }
}

Write-Host "[3/3] Generated Raw 32-Byte Public Key:" -ForegroundColor Green
$hexStr = ($rawPubBytes | ForEach-Object { "0x{0:x2}.toByte()" -f $_ }) -join ", "
$formattedKotlin = @"
val DEFAULT_MAINTAINER_PUBLIC_KEY: ByteArray = byteArrayOf(
    $hexStr
)
"@

Write-Host $formattedKotlin -ForegroundColor White
Write-Host ""
Write-Host "ACTION REQUIRED:" -ForegroundColor Magenta
Write-Host "1. Keep $privateKeyPath securely outside this repository (e.g. in your secure key store or CI secrets)."
Write-Host "2. Copy the formatted Kotlin snippet above into MapCatalogConfig.kt as DEFAULT_MAINTAINER_PUBLIC_KEY."
Write-Host "======================================================" -ForegroundColor Cyan
