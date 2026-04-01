@echo off
REM ============================================================
REM  Sperta Fase 2 - Geração de Keystores, Certificados e Truststores
REM  Requisitos: RSA 2048 bits, JKS keystores, certificados auto-assinados
REM ============================================================

REM --- Caminho do keytool ---
SET KEYTOOL="C:\Program Files\Java\jdk-24\bin\keytool.exe"

REM --- Passwords ---
SET SERVER_KS_PASS=server123
SET CLIENT_TS_PASS=truststore123
SET USER1_KS_PASS=user1pass
SET USER2_KS_PASS=user2pass
SET USER3_KS_PASS=user3pass

REM --- Nomes ---
SET SERVER_KS=server.keystore
SET SERVER_CERT=server.cert
SET CLIENT_TS=client.truststore

echo.
echo ============================================
echo  1. Gerando keystore do SERVIDOR (RSA 2048)
echo ============================================
%KEYTOOL% -genkeypair -alias server -keyalg RSA -keysize 2048 ^
  -dname "CN=SpertaServer, OU=SegC, O=FCUL, L=Lisboa, ST=Lisboa, C=PT" ^
  -validity 365 ^
  -keystore %SERVER_KS% -storepass %SERVER_KS_PASS% ^
  -keypass %SERVER_KS_PASS% ^
  -storetype JKS

echo ============================================
echo  2. Exportando certificado do SERVIDOR
echo ============================================
%KEYTOOL% -exportcert -alias server ^
  -keystore %SERVER_KS% -storepass %SERVER_KS_PASS% ^
  -file %SERVER_CERT%

echo ============================================
echo  3. Criando TRUSTSTORE do cliente
echo ============================================
%KEYTOOL% -importcert -alias server ^
  -file %SERVER_CERT% ^
  -keystore %CLIENT_TS% -storepass %CLIENT_TS_PASS% ^
  -storetype JKS ^
  -noprompt

echo ============================================
echo  4. Gerando keystore do utilizador RODRIGO
echo ============================================
%KEYTOOL% -genkeypair -alias rodrigo -keyalg RSA -keysize 2048 ^
  -dname "CN=rodrigo, OU=SegC, O=FCUL, L=Lisboa, ST=Lisboa, C=PT" ^
  -validity 365 ^
  -keystore rodrigo.keystore -storepass %USER1_KS_PASS% ^
  -keypass %USER1_KS_PASS% ^
  -storetype JKS
%KEYTOOL% -exportcert -alias rodrigo ^
  -keystore rodrigo.keystore -storepass %USER1_KS_PASS% ^
  -file rodrigo.cert

echo ============================================
echo  5. Gerando keystore do utilizador TIAGO
echo ============================================
%KEYTOOL% -genkeypair -alias tiago -keyalg RSA -keysize 2048 ^
  -dname "CN=tiago, OU=SegC, O=FCUL, L=Lisboa, ST=Lisboa, C=PT" ^
  -validity 365 ^
  -keystore tiago.keystore -storepass %USER2_KS_PASS% ^
  -keypass %USER2_KS_PASS% ^
  -storetype JKS
%KEYTOOL% -exportcert -alias tiago ^
  -keystore tiago.keystore -storepass %USER2_KS_PASS% ^
  -file tiago.cert

echo ============================================
echo  6. Gerando keystore do utilizador SIMAO
echo ============================================
%KEYTOOL% -genkeypair -alias simao -keyalg RSA -keysize 2048 ^
  -dname "CN=simao, OU=SegC, O=FCUL, L=Lisboa, ST=Lisboa, C=PT" ^
  -validity 365 ^
  -keystore simao.keystore -storepass %USER3_KS_PASS% ^
  -keypass %USER3_KS_PASS% ^
  -storetype JKS
%KEYTOOL% -exportcert -alias simao ^
  -keystore simao.keystore -storepass %USER3_KS_PASS% ^
  -file simao.cert

echo.
echo ============================================
echo  RESUMO
echo ============================================
echo  SERVIDOR:  %SERVER_KS% (pw: %SERVER_KS_PASS%)
echo  TRUSTSTORE: %CLIENT_TS% (pw: %CLIENT_TS_PASS%)
echo  rodrigo:   rodrigo.keystore (pw: %USER1_KS_PASS%)
echo  tiago:     tiago.keystore   (pw: %USER2_KS_PASS%)
echo  simao:     simao.keystore   (pw: %USER3_KS_PASS%)
echo ============================================
