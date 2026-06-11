# ReplyForge

**Smart auto-reply for WhatsApp** — free & open source.

ReplyForge automatically replies to WhatsApp messages based on custom rules. Create patterns, set responses, and let the app handle the rest.

## Features

### 🎯 Pattern Matching
- **Exact match** — precise keyword trigger
- **Contains** — keyword anywhere in message
- **Starts with / Ends with** — position-based matching
- **Regular expressions** — full regex support
- **Match all / any words** — flexible word matching
- **Fuzzy matching** — Levenshtein distance tolerance
- **Case insensitive** toggle
- **Ignore accents/diacritics**

### 💬 Reply System
- **Single / Random / Sequential** response modes
- **26+ placeholders**: `%name%`, `%message%`, `%time%`, `%date%`, `%day%`, first/last name, URL encoded, random text variants, regex capturing groups (`%1%`, `%2%`)
- **Reply delay** (ms) — simulate human typing
- **Header / Footer** — append to every reply
- **Probability %** — chance-based replies
- **Ignore patterns, groups, individuals**

### 🤖 AI Integration
- **Multi-provider**: OpenAI, Google Gemini, Custom endpoint
- **Per-rule AI config** — choose provider + system prompt per rule
- **Conversation memory** — auto-save per contact (default 20 messages context)
- **Token cost tracking** — prompt/completion/total tokens, daily/monthly stats
- **Smart fallback** — static reply on AI error

### ⏰ Scheduling
- **Active hours** — start/end time per rule
- **Active days** — Mon–Sun chips
- **Holiday rules** — 10 Indonesian national holidays 2026 pre-loaded, toggle per rule
- **Away mode** — global toggle + custom message

### 🛡️ Rate Limiting
- **Min delay** per contact
- **Max replies** per contact per day
- **Prevent repeat** — avoid sending same reply twice
- **Previous rule timeout** — cooldown between different rules

### 👥 Contacts & Groups
- **Allowlist** — specific contacts or groups
- **Receiver type** — Both / Contacts Only / Groups Only

### 📊 Statistics
- Total replies / Today
- Contacts / Groups count
- Process time tracking

### 🎨 Design
- **WhatsApp-style green theme** (#075E54)
- **Material Design 3**
- Clean, intuitive UI

## Screenshots

| Main Screen | Rule Editor | AI Settings |
|:-----------:|:-----------:|:-----------:|
| Rules list with enable/disable toggle | Full rule configuration | Multi-provider AI setup |

## Tech Stack

- **Kotlin** — 100% Kotlin
- **Room Database** — local SQLite with migrations
- **WorkManager** — background processing
- **Retrofit + OkHttp** — AI API calls
- **ViewBinding** — type-safe view access
- **Material Design 3** — WhatsApp green theme
- **AndroidX** — Core, AppCompat, Lifecycle, DataStore

## Requirements

- Android 7.0+ (API 24)
- WhatsApp installed
- Notification access permission

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## How It Works

ReplyForge uses Android's **Notification Listener Service** to detect incoming WhatsApp messages. When a notification arrives:

1. Extracts sender name, message text, and group info
2. Matches against enabled rules using pattern engine
3. Checks scheduling (time, days, holidays), rate limits, and contact allowlist
4. If AI is enabled, queries the configured provider with conversation context
5. Sends reply via WhatsApp notification reply action

**No root required. No WhatsApp API. No unauthorized access.**

## License

MIT License — free to use, modify, and distribute.

## Author

**wahyuzero**
- GitHub: [@wahyuzero](https://github.com/wahyuzero)

---

*ReplyForge is not affiliated with WhatsApp or Meta. It uses Android's public Notification Listener API.*
