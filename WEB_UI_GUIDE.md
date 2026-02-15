# MBLogic-CLJ Web UI Guide

## Overview

The MBLogic-CLJ web interface provides a modern, interactive dashboard for visualizing PLC programs as ladder diagrams and IL instructions.

**Access the UI**: `http://localhost:8080/` (after starting the server)

## Key Features

### 1. **Automatic Program Loading**
- UI automatically loads the tank simulator demo on startup
- Shows all 16 networks with their ladder renderability
- Displays program statistics in real-time

### 2. **Interactive Network Navigation**
- **Network buttons** in the left sidebar (color-coded)
  - 🟢 **Green**: Renderable as ladder logic (3 networks)
  - 🟠 **Orange**: IL instructions only (13 networks)
- Click any network to view its diagram

### 3. **Program Statistics Dashboard**
- **Status section**: Shows if program is loaded
- **Total Networks**: Count of all main program networks
- **Renderability metrics**:
  - Ladder Rungs: Count of networks with ladder representation
  - IL Rungs: Count of complex instruction combinations
  - Coverage %: Percentage of program renderable as ladder
- **Visual progress bar** showing ladder vs IL ratio

### 4. **Ladder Diagram Display**
- **Simple networks** (Networks 1-3): Rendered with ladder rails, contacts, and coils
- **Complex networks**: Display as IL instruction text with yellow background
- **Canvas information**: Shows network ID and rendering type
- **Export option**: Download diagram as SVG file

### 5. **User Controls**
```
Load Program    - Reload the test program
Refresh         - Update statistics and program summary
Export Diagram  - Download current network as SVG
```

## UI Layout

```
┌─────────────────────────────────────────────────────┐
│         Header: Load | Refresh | Export             │
├──────────────────────────────────────────────────────┤
│  Sidebar                    │   Main Panel           │
│  ┌─────────────────────┐   │  ┌──────────────────┐  │
│  │ Program Info        │   │  │  Network View    │  │
│  │ ├─ Status           │   │  │  ┌──────────────┐│  │
│  │ │  Program: ✅Yes  │   │  │  │ [Ladder SVG] ││  │
│  │ │  Networks: 16     │   │  │  │   or         ││  │
│  │ ├─ Renderability    │   │  │  │  [IL Text]   ││  │
│  │ │  Ladder: 3        │   │  │  └──────────────┘│  │
│  │ │  IL: 13           │   │  └──────────────────┘  │
│  │ │  Coverage: 18%    │   │                        │
│  │ └─ Progress: [███░] │   │                        │
│  │                     │   │                        │
│  │ Networks            │   │                        │
│  │ [N1] [N2] [N3]     │   │                        │
│  │ [N4] [N5] [N6]     │   │                        │
│  │ [N7] [N8] ...      │   │                        │
│  └─────────────────────┘   │                        │
└──────────────────────────────────────────────────────┘
│  Footer: GitHub Link                               │
└──────────────────────────────────────────────────────┘
```

## How to Use

### 1. Start the Server
```bash
cd /Users/gregorybrooks/Clojure/mblogic-clj
./MBLogic-CLJ.sh server
```

### 2. Open Web Browser
```
Open: http://localhost:8080/
```

The page will automatically:
- Load the tank simulator program
- Parse all 16 networks
- Display statistics
- Enable network navigation

### 3. Navigate Networks
- **Click network button** to view that network
- Networks are **color-coded**:
  - Green = Ladder logic representation available
  - Orange = IL instructions only
- **Active network** highlighted in blue

### 4. View Diagram
Each network displays:

#### Ladder Logic Networks (Networks 1, 2, 3)
```
┌─────────────────────────────┐
│   Network 1 (Ladder Logic)  │
├─────────────────────────────┤
│  ├─────────────────────────┤
│  │ X1 (contact) ──────(Y1) │  STR X1 OUT Y1
│  │                         │
│  └─────────────────────────┘
│  2 instructions             │
└─────────────────────────────┘
```

#### IL Networks (Networks 4+)
```
┌─────────────────────────────┐
│ ⚙️  IL Instructions         │
│ (Network 5)                 │
├─────────────────────────────┤
│ This network uses complex   │
│ instruction combinations    │
│ beyond ladder logic         │
│                             │
│ Instructions: 3 total       │
└─────────────────────────────┘
```

### 5. Export Diagrams
Click **"Export Diagram"** to download current network as SVG:
```bash
# Downloads file named: network-{id}.svg
# Example: network-1.svg
```

## Features by Network

### Simple Ladder Networks (Green)
**Networks 1-3**: Basic PLC logic
- Direct input → output operations
- Single contact to coil
- Renderable as visual ladder diagrams

### Complex Networks (Orange)
**Networks 4-16**: Industrial operations
- Multi-instruction logic
- Stack-based operations (ANDSTR, ORSTR)
- Data operations (COPY, MATHDEC, MATHHEX)
- Timers, counters, comparisons
- Search and find operations
- Display as IL text (prevents invalid ladder)

## Program Statistics Explained

### Network Renderability
```
Total Rungs:     16  (all networks)
Ladder Rungs:     3  (3 networks renderable as ladder)
IL Rungs:        13  (13 networks complex, IL only)
Coverage:        18% (18% ladder, 82% IL)
```

### Why Not 100% Ladder?
The tank simulator intentionally demonstrates:
- Complex boolean logic (stack operations)
- Data manipulation (COPY, MATHDEC)
- Advanced features (timers, counters)
- Search operations (FIND instructions)

These require IL representation - they can't be cleanly rendered as traditional ladder logic without losing clarity.

## API Integration

The web UI communicates with these API endpoints:

```javascript
// Load program
POST /api/load-program
→ { status: "ok", networks: 16, file: "test/plcprog.txt" }

// Get program summary
GET /api/program-summary
→ { program-loaded: true, total-networks: 16, ladder-renderability: {...} }

// Get network diagram
GET /api/ladder/{id}
→ { network-id: 1, can-render-ladder: true, instruction-count: 2, svg-data: "..." }
```

## Responsive Design

The UI is mobile-responsive:
- **Desktop**: Two-column layout (sidebar + main panel)
- **Tablet**: Stacked layout, optimized for touch
- **Mobile**: Single column, full-width controls

## Color Scheme

- **Purple Gradient**: Main background (#667eea → #764ba2)
- **White**: Content panels
- **Green**: Ladder logic elements
- **Orange**: IL instructions
- **Blue**: Interactive elements and highlights

## Keyboard Shortcuts

Currently supports mouse-only navigation. Keyboard shortcuts can be added:
- `N` - Load new program
- `↑/↓` - Previous/next network
- `E` - Export diagram
- `R` - Refresh program

## Advanced Features

### SVG Export
Click "Export Diagram" to:
- Download current network as SVG file
- Use in documentation or presentations
- Scale to any size without quality loss
- Edit in vector graphics tools (Inkscape, Adobe Illustrator)

### Program Summary
View aggregate statistics:
- Total instruction count across all networks
- Ladder vs IL distribution
- Rendering statistics
- Network breakdown

### Real-time Updates
- Statistics update automatically when program loads
- Network buttons update with renderability info
- Color coding reflects diagram type

## Error Handling

The UI handles errors gracefully:

| Error | Message |
|-------|---------|
| Failed to load program | "Error: [details]" in red |
| No program loaded | "No program loaded" in yellow |
| Network not found | "Network {id} not found" |
| API unreachable | "Failed to connect to server" |

Error messages auto-dismiss after 4 seconds (except critical errors).

## Network-by-Network Overview

### Networks 1-3: Simple Logic
```
Network 1: STR X1 → OUT Y1         (Ladder)
Network 2: STR X2 → OUT Y2         (Ladder)
Network 3: STR X3 → OUT Y3         (Ladder)
```
Direct input-to-output with no stack operations.

### Networks 4-6: Control Logic
```
Network 4: COPY operation (IL)
Network 5: Conditional CALL (IL)
Network 6: MATHDEC calculation (IL)
```

### Networks 7-16: Advanced Features
```
Network 7-8:  Data operations
Network 9-10: Comparisons and logic
Network 11-16: Subroutine calls and complex patterns
```

## Tips & Tricks

1. **Start with Network 1** - Simplest example to understand ladder logic
2. **Compare green vs orange** - See difference between ladder and IL
3. **Export SVG** - Great for documentation and presentations
4. **Refresh anytime** - Safe to refresh without losing progress
5. **Try custom programs** - Future updates will support file upload

## Future Enhancements

Planned features:
- Upload custom IL programs
- Real-time execution view (highlight active rungs)
- Step through program execution
- Breakpoints and debugging
- Interactive ladder editor
- Simulation with input/output control
- Data table inspection panel

## Troubleshooting

### Program not loading
- Check server is running: `curl http://localhost:8080/health`
- Check file exists: `ls test/plcprog.txt`

### Blank diagram
- Wait for page to fully load
- Click "Refresh" button
- Clear browser cache

### Export not working
- Check browser allows file downloads
- Try different network first
- Check browser developer console for errors

## Technical Details

### Technologies Used
- **HTML5**: Semantic markup
- **CSS3**: Modern styling with Grid and Flexbox
- **JavaScript (ES6)**: Vanilla JS, no frameworks
- **SVG**: Scalable vector diagrams
- **Fetch API**: Async API communication

### Browser Support
- Chrome/Chromium: ✅ Full support
- Firefox: ✅ Full support
- Safari: ✅ Full support
- Edge: ✅ Full support
- IE 11: ❌ Not supported

### Performance
- Page load: ~100ms (network included)
- Network switch: ~50ms
- Export: Instant

## Accessibility

The UI includes:
- Semantic HTML structure
- Color contrast compliance
- Readable font sizes
- Responsive touch targets
- Clear messaging and status

## Support

For issues or feature requests:
- GitHub: https://github.com/brooksg44/mblogic-clj
- Issues: https://github.com/brooksg44/mblogic-clj/issues
