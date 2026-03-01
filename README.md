# NEXUS — Personal Document Intelligence System

> *Your documents. Your device. Your AI.*

## What it does
- Indexes PDF, Word, Excel, PowerPoint, TXT, CSV, JSON files locally
- Full-text search across all your documents
- AI-powered semantic queries via your local Gemma API (127.0.0.1:8080)
- Auto-syncs when files change
- HUD-style futuristic interface
- Opens documents directly from search results
- 100% offline — no cloud, no telemetria

## Setup

### 1. Clone and build
```bash
git clone <your-repo>
cd nexus
./gradlew assembleDebug
```
Or push to GitHub and let the CI build the APK for you automatically.

### 2. Install APK
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configure
- Open NEXUS → tap the gear icon
- Set API Host: `127.0.0.1`
- Set Port: `8080` (or whatever your Gemma API uses)
- Add folders to monitor
- Tap FORCE SCAN to index your documents

### 4. Search
Ask anything in natural language:
- "find the contract with Rodriguez"
- "what's in my insurance documents?"
- "show all Excel files about budget"

## Architecture
```
NEXUS/
├── data/
│   ├── local/       # Room database + AI API service
│   ├── model/       # Data models
│   └── repository/  # Business logic
├── ui/
│   ├── components/  # HUD UI components
│   ├── screens/     # Dashboard, Search, Settings
│   ├── theme/       # HUD color scheme
│   └── viewmodel/   # State management
└── service/         # Background indexing worker
```

## GitHub Actions
Push to `main` → APK built automatically → Download from Actions artifacts tab.

## Requirements
- Android 8.0+ (API 26)
- ~200MB RAM for document parsing
- Local AI server running at configured address (optional — app works offline without it)
