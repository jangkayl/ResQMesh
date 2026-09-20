<#
.SYNOPSIS
    Generates an ECDSA (NIST P-256 / secp256r1) keypair for offline map package manifest signing.
.DESCRIPTION
    Creates an ECDSA private key and extracts the X.509 SubjectPublicKeyInfo DER public key.
    Outputs the Kotlin byteArray representation for embedding into MapCatalogConfig.kt.
    Supported natively across all Android versions (API 14+) with zero external dependencies.
    IMPORTANT: Keep the generated private key file (.key / .pem) outside the repository!
.PARAMETER OutputDir
    Directory to save the generated private and public key files.
#>
param(
    [string]$OutputDir = "$HOME\.resqmesh\keys"
)

$ErrorActionPreference = "Stop"

Write-Host "=== ResQMesh Offline Map Signing Keypair Generator (Universal ECDSA) ===" -ForegroundColor Cyan

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$privateKeyPath = Join-Path $OutputDir "map_signing_private_ec.pem"
$publicKeyDerPath = Join-Path $OutputDir "map_signing_public_ec.der"

# 1. Check if OpenSSL is available
$hasOpenssl = (Get-Command openssl -ErrorAction SilentlyContinue) -ne $null

if ($hasOpenssl) {
    Write-Host "[1/3] Generating ECDSA (P-256) private key via OpenSSL..." -ForegroundColor Green
    openssl ecparam -name prime256v1 -genkey -noout -out $privateKeyPath
    Write-Host "Private key saved to: $privateKeyPath" -ForegroundColor Yellow

    Write-Host "[2/3] Extracting public key (X.509 DER)..." -ForegroundColor Green
    openssl ec -in $privateKeyPath -pubout -outform DER -out $publicKeyDerPath
    $derBytes = [System.IO.File]::ReadAllBytes($publicKeyDerPath)
} else {
    Write-Host "[1/3] OpenSSL not found in PATH; using JDK fallback..." -ForegroundColor Yellow
    # Java EC fallback
    $javaCode = @"
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.spec.ECGenParameterSpec;
import java.io.FileOutputStream;

public class KeyGen {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
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
    } else {
        throw "Neither OpenSSL nor JDK (javac/java) was found in PATH. Please install OpenSSL or JDK to generate keys."
    }
}

Write-Host "[3/3] Generated Universal ECDSA Public Key ($($derBytes.Length) bytes):" -ForegroundColor Green
$hexStr = ($derBytes | ForEach-Object { "0x{0:x2}.toByte()" -f $_ }) -join ", "
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
