# ReplyForge

Smart auto-reply for WhatsApp. Free & open source.

## Features
- **Pattern matching** — exact, contains, regex, fuzzy, all/any words
- **Multi-response** — single, random, sequential mode
- **26+ placeholders** — `%name%`, `%message%`, `%time%`, regex groups, random text
- **AI integration** — OpenAI, Gemini, Custom endpoint with conversation memory
- **Scheduling** — active hours, active days, Indonesian holidays 2026
- **Rate limiting** — delay, daily max, prevent repeat
- **Contact/group filtering** — allowlist, receiver type
- **WhatsApp green theme** — Material Design 3

## Build
```
./gradlew assembleDebug
```

## Install
Download latest APK from [Releases](https://github.com/wahyuzero/replyforge/releases)

**Requirements:** Android 7.0+, WhatsApp, notification access

## How It Works
Uses Android Notification Listener Service — no root, no WhatsApp API.

## License
MIT
