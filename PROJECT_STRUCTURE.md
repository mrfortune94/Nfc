# Project File Structure

```
Nfc/
│
├── 📱 Android Application
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/nfc/reader/
│   │   │   │   │   │
│   │   │   │   │   ├── 🎯 MainActivity.kt                   # Main entry point
│   │   │   │   │   │
│   │   │   │   │   ├── 💾 data/                             # Database layer
│   │   │   │   │   │   ├── NfcLog.kt                       # Log entity
│   │   │   │   │   │   ├── CardBackup.kt                   # Backup entity
│   │   │   │   │   │   ├── NfcDao.kt                       # Data access objects
│   │   │   │   │   │   ├── NfcDatabase.kt                  # Room database
│   │   │   │   │   │   └── Converters.kt                   # Type converters
│   │   │   │   │   │
│   │   │   │   │   ├── 📡 nfc/                              # NFC core logic
│   │   │   │   │   │   ├── NfcTagReader.kt                 # Tag reading
│   │   │   │   │   │   ├── NfcNdefWriter.kt                # NDEF writing
│   │   │   │   │   │   ├── ApduHandler.kt                  # APDU commands
│   │   │   │   │   │   └── CardBackupHandler.kt            # Card backup/clone
│   │   │   │   │   │
│   │   │   │   │   ├── 🖥️ ui/                               # User interface
│   │   │   │   │   │   ├── WriteTagActivity.kt             # Write interface
│   │   │   │   │   │   ├── DiagnosticsActivity.kt          # Logs viewer
│   │   │   │   │   │   ├── ApduConsoleActivity.kt          # APDU terminal
│   │   │   │   │   │   └── LogsAdapter.kt                  # RecyclerView adapter
│   │   │   │   │   │
│   │   │   │   │   ├── 🔐 hce/                              # Card emulation
│   │   │   │   │   │   └── CardEmulationService.kt         # HCE service
│   │   │   │   │   │
│   │   │   │   │   └── 🛠️ utils/                            # Utilities
│   │   │   │   │       └── Extensions.kt                   # Helper functions
│   │   │   │   │
│   │   │   │   ├── res/
│   │   │   │   │   ├── layout/                             # UI layouts
│   │   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   │   ├── activity_write_tag.xml
│   │   │   │   │   │   ├── activity_diagnostics.xml
│   │   │   │   │   │   ├── activity_apdu_console.xml
│   │   │   │   │   │   └── item_log.xml
│   │   │   │   │   │
│   │   │   │   │   ├── values/                             # Resources
│   │   │   │   │   │   ├── strings.xml                     # All strings
│   │   │   │   │   │   ├── colors.xml                      # Color palette
│   │   │   │   │   │   └── themes.xml                      # Material theme
│   │   │   │   │   │
│   │   │   │   │   ├── xml/                                # Configuration
│   │   │   │   │   │   ├── nfc_tech_filter.xml            # NFC tech filter
│   │   │   │   │   │   ├── apduservice.xml                # HCE config
│   │   │   │   │   │   └── file_paths.xml                 # FileProvider
│   │   │   │   │   │
│   │   │   │   │   └── mipmap-*/                           # App icons
│   │   │   │   │       └── ic_launcher.png
│   │   │   │   │
│   │   │   │   └── AndroidManifest.xml                     # App manifest
│   │   │   │
│   │   │   └── test/                                       # Unit tests
│   │   │       ├── ApduHandlerTest.kt
│   │   │       └── ExtensionsTest.kt
│   │   │
│   │   ├── build.gradle                                    # App build config
│   │   └── proguard-rules.pro                             # ProGuard rules
│   │
│   ├── build.gradle                                        # Root build config
│   ├── settings.gradle                                     # Project settings
│   └── gradle.properties                                   # Gradle properties
│
└── 📚 Documentation
    ├── README.md                                           # Main documentation
    ├── BUILD.md                                            # Build instructions
    ├── IMPLEMENTATION_SUMMARY.md                           # Feature summary
    └── QUICK_REFERENCE.md                                  # Developer guide
```

## Component Overview

### 🎯 Activities (4)
- **MainActivity**: Primary tag reading interface with real-time display
- **WriteTagActivity**: Tab-based NDEF writing (Text/URL/App)
- **DiagnosticsActivity**: Log viewing with export functionality
- **ApduConsoleActivity**: Interactive APDU command terminal

### 💾 Database (Room)
- **Entities**: NfcLog, CardBackup
- **DAOs**: Type-safe database access with Flow/LiveData
- **Features**: Offline persistence, JSON export, query filtering

### 📡 NFC Core (4 handlers)
- **NfcTagReader**: Multi-standard tag reading (ISO 14443, 15693, 18092)
- **NfcNdefWriter**: NDEF message construction and writing
- **ApduHandler**: ISO 7816 APDU communication (EMV, smart cards)
- **CardBackupHandler**: Mifare Classic and generic card backup

### 🔐 Security
- **CardEmulationService**: Host-based Card Emulation (HCE)
- **AID Support**: Custom Application Identifiers
- **APDU Processing**: SELECT, GET DATA, READ BINARY handling

### 🎨 UI Components
- **Material Design 3**: Modern Android UI components
- **RecyclerView**: Efficient log list display
- **ViewBinding**: Type-safe view access
- **TabLayout**: Multi-mode write interface

## Technology Mapping

### ISO/IEC 14443-A → NfcA
- ATQA, SAK extraction
- Mifare Classic support
- UID reading

### ISO/IEC 14443-B → NfcB
- Application data
- Protocol info
- Type B cards

### ISO/IEC 15693 → NfcV
- Vicinity cards
- DSFID reading
- Extended range

### ISO/IEC 7816 → IsoDep
- APDU commands
- Smart cards
- EMV support

### ISO/IEC 18092 → NfcF
- FeliCa support
- P2P mode
- Type F tags

## Data Flow

```
NFC Tag
  ↓
Android NFC Stack
  ↓
Foreground Dispatch
  ↓
MainActivity/WriteTagActivity/ApduConsoleActivity
  ↓
NfcTagReader / NfcNdefWriter / ApduHandler
  ↓
Database (Room) ← Logging
  ↓
UI Display / Export
```

## Key Features Matrix

| Feature                  | Implementation          | ISO Standard    |
|--------------------------|-------------------------|-----------------|
| UID Reading              | ✅ NfcTagReader         | ISO 14443       |
| NDEF Reading             | ✅ NfcTagReader         | NFC Forum       |
| NDEF Writing             | ✅ NfcNdefWriter        | NFC Forum       |
| Text Records             | ✅ NfcNdefWriter        | RTD_TEXT        |
| URL Records              | ✅ NfcNdefWriter        | RTD_URI         |
| AAR                      | ✅ NfcNdefWriter        | Android         |
| APDU Commands            | ✅ ApduHandler          | ISO 7816        |
| EMV PSE                  | ✅ ApduHandler          | EMVCo           |
| Card Backup              | ✅ CardBackupHandler    | ISO 14443       |
| Mifare Classic           | ✅ CardBackupHandler    | ISO 14443-A     |
| Card Emulation           | ✅ CardEmulationService | ISO 14443-4     |
| Offline Logging          | ✅ Room Database        | -               |
| JSON Export              | ✅ DiagnosticsActivity  | -               |

## Statistics

- **Kotlin Files**: 16
- **XML Resources**: 11
- **Activities**: 4
- **Database Entities**: 2
- **NFC Handlers**: 4
- **Unit Tests**: 2
- **Documentation Files**: 4
- **Lines of Code**: ~3500+

## Build Configuration

- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Java Version**: 17
- **Kotlin**: 1.9.20
- **Gradle**: 8.1.2
