# NFC PRO

A comprehensive Android NFC application for reading, writing, cloning, backing up, and emulating/replaying NFC tags with support for multiple ISO/IEC standards. Now featuring a **premium user interface** with enhanced visual design and smooth animations.

## ✨ Premium UI Features

- **Stunning Splash Screen**: Animated NFC PRO logo with smooth rotation effect
- **Premium App Icons**: Custom-designed launcher icons with gradient NFC branding
- **Modern Material Design**: Card-based layouts with elevation and depth
- **Gradient Backgrounds**: Subtle gradients for a polished, professional look
- **Enhanced Buttons**: Rounded corners, elevation, and premium color scheme
- **Smooth Animations**: Fade-in effects and transitions throughout the app
- **Professional Typography**: Carefully selected fonts and spacing for readability

## Features

### 1. NFC Tag Reading
- **Read Tag UID**: Extract unique identifier from NFC tags
- **Technology Detection**: Identify all supported technologies on the tag
- **NDEF Parsing**: Read and display NDEF messages with multiple record types
- **ISO Standard Detection**: Automatic identification of ISO/IEC standards

### 2. NDEF Writing
- **Text Records**: Write plain text to NFC tags
- **URL Records**: Write URLs for automatic browser launch
- **Android Application Records (AAR)**: Write app launch triggers
- **Combined Records**: Text/URL with AAR for enhanced automation

### 3. APDU Communication
- **ISO-DEP Support**: Full ISO/IEC 14443-4 communication
- **ISO 7816 Commands**: SELECT, READ BINARY, GET DATA
- **EMV Contactless**: Read Payment System Environment (PSE)
- **Raw APDU**: Send custom APDU commands in hex format

### 4. Diagnostics & Logging
- **Offline Logging**: Store all NFC operations in local SQLite database
- **Export Functionality**: Export logs to JSON format
- **History Tracking**: Complete audit trail of all NFC interactions
- **Tag Statistics**: Track read/write operations

### 5. Card Backup & Cloning
- **Card Backup**: Save complete card data for duplication
- **Multi-format Support**: Backup MIFARE Classic, NFC-A, NFC-B, NFC-V tags
- **Secure Storage**: Encrypted local storage for sensitive card data
- **Export Backups**: Export card data to JSON for archival

### 6. Card Emulation & Replay (HCE)
- **Host-based Card Emulation**: Emulate NFC cards without secure element
- **Card Replay**: Replay backed up card data to NFC readers
- **Emulation Profiles**: Store and manage multiple emulation configurations
- **Multi-AID Support**: Support for payment, access control, and transit AIDs
- **Custom Responses**: Configure custom APDU response pairs

### 7. Protected Tag Operations
- **Key Authentication**: Authenticate with MIFARE Classic using Key A/B
- **Key Discovery**: Automatic discovery of working keys using common key dictionary
- **Sector Read/Write**: Read and write individual sectors with authentication
- **Full Dump**: Complete card dump with all accessible sectors
- **Nested Auth Info**: Information about card security characteristics

### 8. Payment Card Analysis (Research Only)
- **EMV Deep Read**: Comprehensive parsing of EMV contactless cards
- **Multi-Network Support**: Visa, Mastercard, AMEX, Discover, JCB, UnionPay, MIR
- **TLV Parser**: Recursive BER-TLV structure parsing with EMV tag dictionary
- **Track2 Parsing**: Extract PAN, expiry, and service code from Track2 data
- **Application Selection**: PPSE/PSE directory reading and AID selection

## Supported NFC Standards

### ISO/IEC 14443
- **Type A and Type B**: Full support for both signaling protocols
- **RF Field**: Proper handling of RF communication
- **Modulation**: Support for different modulation schemes
- **Anti-collision**: Automatic handling of multiple tags
- **Protocol Layers**: Complete protocol stack implementation

### EMV Contactless Specifications
- **EMVCo Standards**: Support for EMV contactless payments
- **PSE Reading**: Payment System Environment access
- **ISO 14443 Compliance**: Built on ISO 14443 standards
- **Payment Card Brands**: Visa, Mastercard, AMEX, Discover, JCB, UnionPay, MIR

### ISO/IEC 7816
- **APDU Commands**: Full Application Protocol Data Unit support
- **Smart Card Communication**: Industry-standard commands
- **File System Access**: SELECT and READ operations

### ISO/IEC 18092
- **NFC Peer-to-Peer Mode**: Device-to-device communication
- **Type F (FeliCa)**: Japanese contactless standard support

### ISO/IEC 15693
- **Vicinity Cards**: Support for longer-range NFC tags
- **DSFID**: Data Storage Format Identifier reading
- **Type V Tags**: Full compatibility

## Technology Support

- **NfcA** (ISO 14443-A)
- **NfcB** (ISO 14443-B)
- **NfcF** (ISO 18092 / FeliCa)
- **NfcV** (ISO 15693)
- **IsoDep** (ISO 14443-4 / ISO 7816)
- **Ndef** (NFC Data Exchange Format)
- **NdefFormatable**
- **MifareClassic**
- **MifareUltralight**

## RFID Tag Support

This app supports **13.56 MHz RFID/NFC tags** including:
- ✅ MIFARE Classic (1K, 4K) - Common access cards
- ✅ MIFARE Ultralight - Event tickets, transit cards
- ✅ MIFARE DESFire - Secure access cards
- ✅ ISO 14443-A/B - Proximity cards
- ✅ ISO 15693 - Vicinity cards (longer range)
- ✅ FeliCa - Japanese transit cards

**Not Supported (Hardware Limitation):**
- ❌ 125 kHz RFID tags (EM4100, HID ProxCard, T5577)
- ❌ 134 kHz animal tracking chips

> **Note:** Standard Android phones only have 13.56 MHz NFC hardware. Low-frequency 125 kHz RFID tags require specialized hardware that is not included in consumer smartphones.

## Requirements

- Android device with NFC hardware
- Android SDK 21 (Lollipop) or higher
- NFC enabled in device settings

## Permissions

The app requires the following permissions:
- **NFC Permission** (`android.permission.NFC`) - Granted automatically at install time
- **Storage Permission** - For exporting logs (Android 9 and below only)

> **Troubleshooting NFC:** If NFC is not reading tags, ensure NFC is enabled in your device settings. The app will prompt you to open NFC settings if it detects NFC is disabled.

## Installation

1. Clone the repository:
```bash
git clone https://github.com/mrfortune94/Nfc.git
cd Nfc
```

2. Open in Android Studio
3. Build and run on NFC-enabled device

## Usage

### Reading NFC Tags
1. Launch the app
2. Hold your device near an NFC tag
3. View tag information including:
   - UID
   - Technologies
   - ISO Standard
   - NDEF data
   - Memory size
   - Technical parameters

### Writing to NFC Tags
1. Tap "Write Tag" button
2. Select write mode (Text/URL/App)
3. Enter the data to write
4. Hold device near tag
5. Confirmation message displays on success

### Viewing Diagnostics
1. Tap "Diagnostics" button
2. View all logged NFC operations
3. Export logs as JSON
4. Clear history if needed

### Card Emulation
1. Enable in Android NFC settings
2. Configure desired AID
3. Phone acts as NFC card when near reader

## Architecture

### Data Layer
- Room database for offline storage
- DAO interfaces for data access
- Entity models for NFC logs and card backups

### NFC Layer
- `NfcTagReader`: Tag reading and parsing
- `NfcNdefWriter`: NDEF message writing
- `ApduHandler`: ISO 7816 APDU communication
- `CardEmulationService`: HCE implementation

### UI Layer
- `MainActivity`: Tag reading interface
- `WriteTagActivity`: Tag writing interface
- `DiagnosticsActivity`: Log viewing and export
- MVVM pattern with ViewModels and LiveData

## Technical Details

### ATQA and SAK (ISO 14443-A)
- **ATQA**: Answer to Request Type A - identifies tag type
- **SAK**: Select Acknowledge - indicates protocol support

### APDU Structure
```
CLA | INS | P1 | P2 | Lc | Data | Le
```
- **CLA**: Class byte
- **INS**: Instruction
- **P1/P2**: Parameters
- **Lc**: Data length
- **Le**: Expected response length

### NDEF Record Types
- **Text**: Plain text with language code
- **URI**: Web URLs and custom URIs
- **AAR**: Android Application Records
- **Smart Poster**: Complex multi-record messages

## Security Considerations

### General Security
- Sensitive card data encrypted before storage
- User consent required for card cloning/emulation
- HCE operates in isolated process
- Disclaimer required on first launch

### Payment Card Research Notes
The payment card analysis features are provided **strictly for educational and security research purposes**:

- **No Transaction Capability**: This app cannot make payments or create valid transactions
- **Research Tool**: Designed for security researchers to understand EMV protocols
- **No Cryptographic Bypass**: Cannot bypass card security (CDA, DDA, SDA signatures)
- **Read-Only Analysis**: Extracts publicly readable data per EMV specifications
- **Compliance**: All features comply with publicly documented ISO/EMV standards

### Legal Considerations
- Only use with cards you own
- Unauthorized access to NFC systems may violate computer fraud laws
- Payment card research should comply with PCI-DSS and local regulations
- This tool is not intended for fraud or unauthorized access

### Key Management for Mifare Classic
- Built-in key dictionary includes only publicly known keys
- Users can add their own keys for tags they own
- Keys are stored locally and not transmitted

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit pull requests.

## Troubleshooting

### NFC not working
- Ensure NFC is enabled in device settings (the app will prompt you to open settings)
- The NFC permission is granted automatically at install - no runtime permission needed
- Verify device has NFC hardware
- Try restarting the app after enabling NFC

### RFID tags not reading
- Verify the tag operates at **13.56 MHz** (NFC frequency)
- **125 kHz RFID tags cannot be read** by Android phones (hardware limitation)
- Common 125 kHz tags that won't work: EM4100, HID ProxCard, T5577, animal chips

### Tags not detected
- Hold device steady near tag
- Try different tag positions (NFC antenna location varies by phone)
- Some tags may be read-only or locked
- Remove phone case if it's thick or metallic

### Writing fails
- Check tag is writable (not read-only)
- Ensure sufficient memory on tag
- Verify NDEF format compatibility

## References

- [ISO/IEC 14443 Standard](https://www.iso.org/standard/73599.html)
- [NFC Forum Specifications](https://nfc-forum.org/our-work/specifications-and-application-documents/)
- [Android NFC Developer Guide](https://developer.android.com/guide/topics/connectivity/nfc)
- [EMVCo Contactless Specifications](https://www.emvco.com/)