# NFC Reader & Writer

A comprehensive Android NFC application for reading, writing, and emulating NFC tags with support for multiple ISO/IEC standards.

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

### 5. Card Operations
- **Card Backup**: Save card data for cloning
- **Access Card Cloning**: Duplicate supported access cards
- **Credential Storage**: Secure storage of card credentials

### 6. Card Emulation (HCE)
- **Host-based Card Emulation**: Emulate NFC cards without secure element
- **Credential Emulation**: Emulate stored access cards
- **Custom AID Support**: Configure custom Application Identifiers

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

## Requirements

- Android device with NFC hardware
- Android SDK 21 (Lollipop) or higher
- NFC enabled in device settings

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

- Sensitive card data encrypted before storage
- No default keys hardcoded
- User consent required for card cloning
- HCE operates in isolated process

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit pull requests.

## Troubleshooting

### NFC not working
- Ensure NFC is enabled in device settings
- Check app has NFC permissions
- Verify device has NFC hardware

### Tags not detected
- Hold device steady near tag
- Try different tag positions
- Some tags may be read-only or locked

### Writing fails
- Check tag is writable (not read-only)
- Ensure sufficient memory on tag
- Verify NDEF format compatibility

## References

- [ISO/IEC 14443 Standard](https://www.iso.org/standard/73599.html)
- [NFC Forum Specifications](https://nfc-forum.org/our-work/specifications-and-application-documents/)
- [Android NFC Developer Guide](https://developer.android.com/guide/topics/connectivity/nfc)
- [EMVCo Contactless Specifications](https://www.emvco.com/)