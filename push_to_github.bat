@echo off
title Push Screen Censor to GitHub
chcp 65001 >nul
echo ============================================================
echo      PUSH SCREEN CENSOR TO GITHUB (CLOUD BUILD APK)
echo ============================================================
echo.
echo 1. ไปที่ https://github.com/new แล้วสร้าง Repository ใหม่
echo    (ตั้งชื่ออะไรก็ได้ เช่น screen-censor-android และตั้งเป็น Private ได้)
echo.
echo 2. นำ URL ของ Repository มาวางที่นี่ (เช่น https://github.com/username/repo.git)
echo.
set /p REPO_URL="วาง URL ของ GitHub Repo ที่นี่: "

if "%REPO_URL%"=="" (
    echo ไม่ได้รับ URL สิ้นสุดการทำงาน
    pause
    exit /b
)

git remote remove origin 2>nul
git remote add origin %REPO_URL%
git branch -M main
echo.
echo กำลัง Push โค้ดและโมเดลขึ้น GitHub...
git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================================
    echo [สำเร็จ] Push ขึ้น GitHub เรียบร้อยแล้ว!
    echo.
    echo ระบบ GitHub Actions จะเริ่มคอมไพล์ APK ให้อัตโนมัติทันที
    echo เข้าไปที่แท็บ Actions ในหน้า GitHub เพื่อรอโหลดไฟล์ APK ได้เลยครับ!
    echo ============================================================
) else (
    echo.
    echo [แจ้งเตือน] ไม่สามารถ Push ได้ กรุณาตรวจสอบสิทธิ์ GitHub ของคุณ
)
echo.
pause
