# NFC Application - Quick Reference

## Quick Start

### 1. Reading an NFC Tag
```kotlin
// Tag is automatically detected via NFC intent
val tagInfo = NfcTagReader().readTag(tag)

// Access tag information
val uid = tagInfo.uid
val technologies = tagInfo.technologies
val isoStandard = tagInfo.isoStandard
val ndefData = tagInfo.ndefInfo
```

### 2. Writing to an NFC Tag
```kotlin
val writer = NfcNdefWriter()

// Write text
writer.writeText(tag, "Hello NFC!")

// Write URL
writer.writeUrl(tag, "https://example.com")

// Write app launch
writer.writeAppLaunch(tag, "com.example.app")
```

### 3. Sending APDU Commands
```kotlin
val apduHandler = ApduHandler()

// SELECT command
val aid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
val result = apduHandler.selectApplication(tag, aid)

// Raw APDU
val command = "00A4040007A0000000031010"
val response = apduHandler.sendRawApdu(tag, apduHandler.parseHexCommand(command))
```

### 4. Backing Up a Card
```kotlin
val backupHandler = CardBackupHandler()
val result = backupHandler.backupCard(tag, "My Access Card")

if (result.success) {
    // Save backup to database
    database.cardBackupDao().insert(result.backup!!)
}
```

## Common APDU Commands

### SELECT Application
```
00 A4 04 00 [Lc] [AID] [Le]

Example:
00 A4 04 00 07 A0 00 00 00 03 10 10 00
```

### READ BINARY
```
00 B0 [P1] [P2] [Le]

Example (read 16 bytes from offset 0):
00 B0 00 00 10
```

### GET DATA
```
00 CA [P1] [P2] [Le]

Example (get card data):
00 CA 00 00 00
```

### EMV PSE
```
00 A4 04 00 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31
(SELECT "1PAY.SYS.DDF01")
```

## ISO Standard Reference

### ISO 14443-A Tags
- **Detection**: Check for `android.nfc.tech.NfcA`
- **UID**: Available via `tag.id`
- **ATQA**: `NfcA.atqa`
- **SAK**: `NfcA.sak`
- **Examples**: Mifare Classic, Mifare Ultralight, NTAG

### ISO 14443-B Tags
- **Detection**: Check for `android.nfc.tech.NfcB`
- **App Data**: `NfcB.applicationData`
- **Protocol**: `NfcB.protocolInfo`
- **Examples**: Calypso, some access cards

### ISO 15693 Tags (NFC-V)
- **Detection**: Check for `android.nfc.tech.NfcV`
- **DSFID**: `NfcV.dsfId`
- **Range**: Up to 1.5 meters
- **Examples**: Vicinity cards, library tags

### ISO 7816 (ISO-DEP)
- **Detection**: Check for `android.nfc.tech.IsoDep`
- **APDU**: Full command support
- **Examples**: EMV cards, JavaCard, some access cards

## Technology Detection

```kotlin
when {
    techList.contains("android.nfc.tech.NfcA") -> {
        // ISO 14443-A
        val nfcA = NfcA.get(tag)
    }
    techList.contains("android.nfc.tech.NfcB") -> {
        // ISO 14443-B
        val nfcB = NfcB.get(tag)
    }
    techList.contains("android.nfc.tech.NfcV") -> {
        // ISO 15693
        val nfcV = NfcV.get(tag)
    }
    techList.contains("android.nfc.tech.IsoDep") -> {
        // ISO 7816
        val isoDep = IsoDep.get(tag)
    }
}
```

## NDEF Record Types

### Text Record
```kotlin
TNF: WELL_KNOWN (0x01)
Type: RTD_TEXT ("T")
Payload: [Status byte][Language][Text]
```

### URI Record
```kotlin
TNF: WELL_KNOWN (0x01)
Type: RTD_URI ("U")
Payload: [URI identifier code][URI]
```

### Android Application Record
```kotlin
TNF: EXTERNAL_TYPE (0x04)
Type: "android.com:pkg"
Payload: Package name
```

## Status Words (APDU Responses)

| SW1-SW2 | Meaning |
|---------|---------|
| 90 00   | Success |
| 61 XX   | More data available (XX bytes) |
| 62 XX   | Warning/State unchanged |
| 63 XX   | Warning/State changed |
| 64 XX   | Error/State unchanged |
| 65 XX   | Error/State changed |
| 67 XX   | Wrong length |
| 68 XX   | Function not supported |
| 69 XX   | Command not allowed |
| 6A XX   | Wrong parameters |
| 6B XX   | Wrong P1-P2 |
| 6C XX   | Wrong Le field |
| 6D XX   | Instruction not supported |
| 6E XX   | Class not supported |
| 6F XX   | Technical problem |

## Database Queries

### Get All Logs
```kotlin
database.nfcLogDao().getAllLogs().collect { logs ->
    // Process logs
}
```

### Get Logs by Operation
```kotlin
database.nfcLogDao().getLogsByOperation("READ").collect { logs ->
    // Process read operations
}
```

### Export Logs
```kotlin
val gson = Gson()
val logs = database.nfcLogDao().getAllLogs().first()
val json = gson.toJson(logs)
// Write to file
```

## Permissions

### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

### Runtime Checks
```kotlin
val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
if (nfcAdapter == null) {
    // Device doesn't support NFC
} else if (!nfcAdapter.isEnabled) {
    // NFC is disabled
}
```

## Foreground Dispatch

```kotlin
override fun onResume() {
    super.onResume()
    val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
}

override fun onPause() {
    super.onPause()
    nfcAdapter?.disableForegroundDispatch(this)
}
```

## Common Issues & Solutions

### Issue: Tag Lost Exception
```kotlin
try {
    // NFC operation
} catch (e: TagLostException) {
    // Tag moved away, retry or inform user
}
```

### Issue: IO Exception
```kotlin
try {
    // NFC operation
} catch (e: IOException) {
    // Communication error, check tag proximity
}
```

### Issue: Format Exception
```kotlin
try {
    val ndef = Ndef.get(tag)
    ndef?.use { it.ndefMessage }
} catch (e: FormatException) {
    // Tag not NDEF formatted or corrupted
}
```

## Utility Functions

### Hex Conversion
```kotlin
// ByteArray to Hex
val hex = byteArray.toHexString()

// Hex to ByteArray
val bytes = "1234ABCD".hexToByteArray()
```

### Date Formatting
```kotlin
val dateString = timestamp.toDateString()
// Output: "2024-01-01 12:00:00"
```

## Testing Tips

1. **Use Real Devices**: Emulators don't support NFC
2. **Multiple Tag Types**: Test with various standards
3. **Edge Cases**: Empty tags, locked tags, corrupted tags
4. **Logging**: Enable verbose logging for debugging
5. **Battery**: NFC operations consume battery

## Performance Optimization

- Close NFC connections promptly
- Use coroutines for database operations
- Cache tag information when possible
- Implement timeout for operations
- Handle background/foreground transitions

## Security Best Practices

- Never hardcode keys
- Validate all input data
- Use encryption for sensitive data
- Implement user consent for operations
- Log security-relevant events
- Regular security audits

---

For detailed documentation, see README.md and BUILD.md
