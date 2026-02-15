# Building and Running the NFC Application

## Prerequisites

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **JDK**: Java Development Kit 17
- **Android SDK**: API Level 21 (Lollipop) minimum, API Level 34 (Android 14) target
- **Physical Device**: Android device with NFC hardware (emulator does not support NFC)

## Build Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/mrfortune94/Nfc.git
cd Nfc
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Select "Open an Existing Project"
3. Navigate to the cloned repository
4. Wait for Gradle sync to complete

### 3. Build the Project

#### From Android Studio:
- Click **Build** → **Make Project** (Ctrl+F9 / Cmd+F9)
- Or click **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**

#### From Command Line:
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean and build
./gradlew clean build
```

### 4. Run Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

## Running the Application

### On Physical Device

1. **Enable Developer Options**:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings → Developer Options
   - Enable "USB Debugging"

2. **Enable NFC**:
   - Go to Settings → Connected Devices → Connection Preferences
   - Enable NFC
   - Enable Android Beam (if available)

3. **Install and Run**:
   ```bash
   # Install debug APK
   ./gradlew installDebug
   
   # Or from Android Studio
   # Click Run (Shift+F10 / Ctrl+R)
   ```

## Project Structure

```
Nfc/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/nfc/reader/
│   │   │   │   ├── data/              # Database entities and DAOs
│   │   │   │   ├── hce/               # Host Card Emulation service
│   │   │   │   ├── nfc/               # NFC reading/writing/APDU handlers
│   │   │   │   ├── ui/                # Activities and UI components
│   │   │   │   ├── utils/             # Utility functions
│   │   │   │   └── MainActivity.kt
│   │   │   ├── res/                   # Resources (layouts, strings, etc.)
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                      # Unit tests
│   │   └── androidTest/               # Instrumented tests
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Key Dependencies

- **AndroidX Core**: 1.12.0
- **Material Components**: 1.11.0
- **Room Database**: 2.6.1
- **Coroutines**: 1.7.3
- **Gson**: 2.10.1

## Gradle Configuration

### Minimum Requirements
- **Compile SDK**: 34
- **Min SDK**: 21
- **Target SDK**: 34
- **Java Version**: 17
- **Kotlin Version**: 1.9.20

## Troubleshooting

### Build Issues

**Problem**: Gradle sync fails
```
Solution: File → Invalidate Caches / Restart
```

**Problem**: Dependency resolution errors
```
Solution: Check internet connection and proxy settings
         Update Gradle to latest version
```

### Runtime Issues

**Problem**: NFC not working
```
Solution: Verify device has NFC hardware
         Enable NFC in device settings
         Grant app permissions
```

**Problem**: App crashes on tag detection
```
Solution: Check Android logs (Logcat)
         Verify tag is supported
         Check tag is not locked
```

## Advanced Configuration

### Custom Build Types

Add to `app/build.gradle`:

```gradle
buildTypes {
    debug {
        applicationIdSuffix ".debug"
        versionNameSuffix "-debug"
        debuggable true
    }
    
    staging {
        applicationIdSuffix ".staging"
        versionNameSuffix "-staging"
        debuggable true
        minifyEnabled false
    }
    
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

### Signing Configuration

Create `keystore.properties` in project root:

```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=your_key_alias
storeFile=path/to/keystore.jks
```

Add to `app/build.gradle`:

```gradle
signingConfigs {
    release {
        def keystorePropertiesFile = rootProject.file("keystore.properties")
        def keystoreProperties = new Properties()
        keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
        
        storeFile file(keystoreProperties['storeFile'])
        storePassword keystoreProperties['storePassword']
        keyAlias keystoreProperties['keyAlias']
        keyPassword keystoreProperties['keyPassword']
    }
}
```

## Testing NFC Features

### Required Hardware
- NFC-enabled Android device
- Test NFC tags (various types recommended):
  - NTAG213/215/216
  - Mifare Classic 1K/4K
  - Mifare Ultralight
  - ISO 15693 tags

### Test Scenarios

1. **Tag Reading**:
   - Launch app
   - Hold tag near device
   - Verify UID and tech types display
   - Check NDEF data parsing

2. **Tag Writing**:
   - Navigate to Write Tag screen
   - Enter text/URL/app package
   - Hold tag near device
   - Verify success message

3. **APDU Commands**:
   - Navigate to APDU Console
   - Scan ISO-DEP compatible tag
   - Send test commands
   - Verify responses

4. **Diagnostics**:
   - Navigate to Diagnostics
   - View logged operations
   - Export logs
   - Verify JSON format

## Performance Optimization

### ProGuard Rules

Add to `proguard-rules.pro`:

```proguard
# Keep NFC classes
-keep class android.nfc.** { *; }

# Keep Room entities
-keep class com.nfc.reader.data.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
```

## Continuous Integration

### GitHub Actions Example

Create `.github/workflows/android.yml`:

```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Run tests
      run: ./gradlew test
```

## Resources

- [Android NFC Guide](https://developer.android.com/guide/topics/connectivity/nfc)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Material Design](https://material.io/develop/android)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
