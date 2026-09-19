# Screen Censor AI (Android Proof-of-Concept)

แอปพลิเคชันต้นแบบสำหรับ **Android** สำหรับการตรวจจับและเซ็นเซอร์หน้าจอแบบเรียลไทม์ (Real-time On-Device Screen Blocker) ด้วยโมเดล **YOLOv11 Nano (320x320)** ผ่าน **ONNX Runtime Mobile**

---

## 🌟 คุณสมบัติเด่น (Features)

1. **ประมวลผลบนเครื่อง 100% (Offline & Private)**:
   - ไม่มีการส่งภาพหน้าจอออกนอกเครื่อง ข้อมูลปลอดภัย 100%
   - ขับเคลื่อนด้วยโมเดล `model.onnx` (YOLOv11 Nano 320x320) ขนาดเพียง ~10 MB
2. **ดักจับหน้าจอแบบเบาและเร็ว (MediaProjection API)**:
   - รันในเบื้องหลังผ่าน `ForegroundService` ที่อัตรา ~20 FPS ลื่นไหลและประหยัดแบตเตอรี่
3. **เลเยอร์เซ็นเซอร์สัมผัสทะลุได้ (Touch-through Transparent Overlay)**:
   - ใช้ `WindowManager` ชนิด `TYPE_APPLICATION_OVERLAY` พร้อม flag `FLAG_NOT_TOUCHABLE`
   - เมื่อตรวจพบจุด 18+ จะวาดกล่องสีดำทับตำแหน่งนั้นทันที
   - ผู้ใช้ยังสามารถสัมผัส เลื่อน และใช้งานแอปอื่นๆ (เบราว์เซอร์, Facebook, X/Twitter, TikTok ฯลฯ) ได้ตามปกติ
4. **ปรับแต่งตามใจชอบ**:
   - ปรับค่าความไว (Confidence Threshold Slider: 10% - 90%)
   - สวิตช์เลือกเปิด/ปิดการเซ็นเซอร์เฉพาะจุด:
     - เต้านม (Breasts)
     - อวัยวะเพศ (Genitalia)
     - บั้นท้าย (Buttocks / Anus)
     - เสื้อผ้าวาบหวิว / บิกินี่ (Covered / Bikini)

---

## 📁 โครงสร้างโปรเจกต์ (Project Structure)

```text
ScreenCensorAndroid/
├── app/
│   ├── build.gradle.kts                   # การตั้งค่าโมดูล และ Dependencies (ONNX Runtime 1.20)
│   ├── proguard-rules.pro                 # กฎ Proguard ป้องกันคลาส ONNX ถูกตัดทอน
│   └── src/main/
│       ├── AndroidManifest.xml            # สิทธิ์ MediaProjection, Overlay, Service
│       ├── assets/
│       │   └── model.onnx                 # โมเดล YOLOv11 Nano 320x320
│       ├── java/com/screencensor/
│       │   ├── MainActivity.kt            # หน้าจอควบคุม, ขอสิทธิ์ และเปิด/ปิดระบบ
│       │   ├── ai/
│       │   │   └── YoloDetector.kt        # ตัวประมวลผล ONNX Runtime + NMS ถอดรหัสพิกัด
│       │   ├── model/
│       │   │   └── DetectionResult.kt     # โครงสร้างข้อมูลคลาสทั้ง 16 คลาส
│       │   └── service/
│       │       ├── CensorOverlayView.kt   # เลเยอร์วาดกล่องดำทับพิกัด
│       │       └── ScreenCensorService.kt # เซอร์วิสดักจับหน้าจอและรัน AI Loop
│       └── res/                           # Layouts, Colors, Icons, Themes
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 วิธีการ Build และติดตั้งลงในมือถือ

### วิธีที่ 1: เปิดใน Android Studio (แนะนำและง่ายที่สุด)
1. ติดตั้ง **Android Studio** (หากยังไม่มี สามารถดาวน์โหลดได้จาก [developer.android.com](https://developer.android.com/studio))
2. เปิดโปรแกรม Android Studio -> เลือก **Open** -> ไปที่โฟลเดอร์:
   `C:\Users\paixd\Downloads\ScreenCensorAndroid`
3. รอให้ Android Studio ทำการ Sync Gradle ให้เสร็จสิ้นโดยอัตโนมัติ
4. เสียบสาย USB เข้ากับมือถือ Android (เปิดโหมด Developer Options -> USB Debugging)
5. กดปุ่ม **Run (▶)** สีเขียวด้านบนเพื่อคอมไพล์และติดตั้งลงมือถือทันที

### วิธีที่ 2: Build ผ่าน Command Line ด้วย Gradle
ในโฟลเดอร์โปรเจกต์ สามารถรันคำสั่ง:
```bash
./gradlew assembleDebug
```
ไฟล์ APK จะถูกสร้างขึ้นที่:
`app/build/outputs/apk/debug/app-debug.apk`

ติดตั้งลงมือถือผ่าน ADB:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 วิธีการเปิดใช้งานบนมือถือครั้งแรก

1. เปิดแอป **Screen Censor AI** บนมือถือ
2. กดปุ่ม **"เริ่มการทำงาน (Start Censor)"**
3. ระบบ Android จะแสดงหน้าต่างขอสิทธิ์ 2 อย่าง:
   - **Display over other apps (แสดงทับแอปอื่น)**: ให้แตะอนุญาต (เพื่อให้วาดกล่องดำทับจอได้)
   - **Start recording or casting (การแชร์หรือบันทึกหน้าจอ)**: ให้แตะ "เริ่มเลย (Start now)"
4. แถบสถานะจะเปลี่ยนเป็น **สีเขียว (Active)** และมีไอคอนแสดงในแถบการแจ้งเตือน (Notification)
5. สลับออกไปเปิดแอปใดๆ ตามต้องการ เมื่อมีเนื้อหา 18+ ปรากฏบนหน้าจอ กล่องดำจะขึ้นมาเซ็นเซอร์จุดนั้นทันที!
