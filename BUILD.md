# Build Instructions for Dose Certa

## Prerequisites

You need either:
- **Option A**: Android Studio (Recommended)
- **Option B**: Command line with JDK 17+

## Option A: Build with Android Studio (Recommended)

This is the easiest method and handles all dependencies automatically.

### Steps:

1. **Install Android Studio**
   - Download from: https://developer.android.com/studio
   - Install with default settings
   - Launch Android Studio

2. **Open Project**
   ```
   File → Open → Select d:\Code\DoseCerta folder
   ```

3. **Wait for Sync**
   - Android Studio will automatically:
     - Download Gradle wrapper JAR
     - Download all dependencies
     - Build the project
   - This takes 2-5 minutes on first run

4. **Build Project**
   ```
   Build → Make Project (or Ctrl+F9)
   ```

5. **Run on Emulator/Device**
   ```
   Run → Run 'app' (or Shift+F10)
   ```

### Troubleshooting Android Studio

**"Gradle sync failed"**
- Click "Try Again" button
- Or: File → Invalidate Caches → Invalidate and Restart

**"SDK not found"**
- File → Settings → Appearance & Behavior → System Settings → Android SDK
- Install Android 13.0 (API 33) and Android 14.0 (API 34)

**"Build failed"**
- Check build output for specific errors
- Verify Java 17+ is installed

---

## Option B: Build from Command Line

Requires Java Development Kit (JDK) 17 or newer.

### 1. Check Java Version
```powershell
java -version
```
Should show version 17 or higher. If not, install from: https://adoptium.net/

### 2. Download Gradle Wrapper JAR

The gradle-wrapper.jar file is missing (it's a binary file). You need to either:

**A. Get it from another Gradle project:**
```powershell
# Copy from another Android project
copy "C:\path\to\another\project\gradle\wrapper\gradle-wrapper.jar" "d:\Code\DoseCerta\gradle\wrapper\"
```

**B. Or download directly:**
1. Download from: https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar
2. Save to: `d:\Code\DoseCerta\gradle\wrapper\gradle-wrapper.jar`

### 3. Build the Project
```powershell
cd d:\Code\DoseCerta

# Build debug APK
.\gradlew.bat assembleDebug

# Build release APK
.\gradlew.bat assembleRelease
```

### 4. Find the APK
```
app\build\outputs\apk\debug\app-debug.apk
```

### 5. Install on Device
```powershell
# If you have ADB installed
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## Common Issues

### Issue: "gradlew.bat is not recognized"
**Solution**: Make sure you're in the `d:\Code\DoseCerta` directory

### Issue: "Could not find or load main class org.gradle.wrapper.GradleWrapperMain"
**Solution**: The gradle-wrapper.jar is missing. Download it (see Option B, step 2)

### Issue: "SDK location not found"
**Solution**: Create `local.properties` file:
```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```
Replace YOUR_USERNAME with your Windows username.

### Issue: "Execution failed for task ':app:compileDebugKotlin'"
**Solution**: 
1. Clean the project: `.\gradlew.bat clean`
2. Rebuild: `.\gradlew.bat assembleDebug`

---

## Recommended: Use Android Studio

Building Android apps from command line can be complex. **Android Studio is strongly recommended** because it:
- ✅ Handles all Gradle setup automatically
- ✅ Provides visual debugging tools
- ✅ Shows build errors clearly
- ✅ Includes Android emulator
- ✅ Has code completion and refactoring
- ✅ Manages SDK versions automatically

---

## Next Steps After Building

1. **Test the App**: Run on emulator or device
2. **Check All Screens**: Navigate through Início, Histórico, Medicamentos
3. **Test Dark Mode**: Change system theme to dark mode
4. **Load Test Data**: Add sample medications to test the UI
5. **Review Code**: Explore the codebase in Android Studio

---

**Need Help?**
- Android Studio docs: https://developer.android.com/studio/intro
- Gradle docs: https://docs.gradle.org/
- Kotlin docs: https://kotlinlang.org/docs/
