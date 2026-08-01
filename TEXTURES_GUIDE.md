# Guida Dettagliata per gli Asset Grafici (Texture) di CS:GO Minecraft Mod

Questa guida elenca **tutti gli asset visivi e le texture** necessari per il mod, inclusi i percorsi esatti dei file, le dimensioni trasparenti e le descrizioni dettagliate stile Pixel Art.

---

## 📁 Struttura della Cartella delle Texture

Tutti i file delle texture vanno posizionati dentro la seguente struttura di cartelle nel progetto:
`src/main/resources/assets/csgo_mc/textures/`

```
csgo_mc/
└── textures/
    ├── block/
    │   ├── bomb_site.png
    │   └── c4_bomb.png
    ├── item/
    │   ├── bomb_site_wand.png
    │   ├── c4_bomb.png
    │   ├── ct_boots.png
    │   ├── ct_chestplate.png
    │   ├── ct_helmet.png
    │   ├── ct_leggings.png
    │   ├── defusal_kit.png
    │   ├── t_boots.png
    │   ├── t_chestplate.png
    │   ├── t_helmet.png
    │   └── t_leggings.png
    ├── entity/
    │   ├── ct.png
    │   └── t.png
    └── models/
        └── armor/
            ├── ct_layer_1.png
            ├── ct_layer_2.png
            ├── t_layer_1.png
            └── t_layer_2.png
```

---

## 1. 🧱 Texture dei Blocchi (`textures/block/`)

### 🟢 `bomb_site.png`
- **Dimensione**: `16x16` pixel (o `32x32` HD).
- **Descrizione Visiva**: Blocco speciale che identifica la zona di piazzamento (Bomb Site A o B).
- **Consigli di Disegno**:
  - **Sfondo**: Grigio cemento armato scuro con texture ruvida.
  - **Dettaglio**: Una grande lettere rossa **"A"** o **"B"** disegnata al centro.
  - **Bordo**: Righe diagonali di avvertimento gialle e nere (hazard stripes) lungo i bordi superiori e inferiori.

### 🧨 `c4_bomb.png`
- **Dimensione**: `16x16` pixel per faccia (oppure mappa cubica 64x32 se associata a modello personalizzato).
- **Descrizione Visiva**: C4 tattico di CS:GO.
- **Consigli di Disegno**:
  - **Fronte**: Tastierino numerico digitale grigio chiaro con uno schermino LED verde/rosso e i panetti di esplosivo C4 beige tenuti insieme da nastro adesivo nero.
  - **Lati**: Fili rosso, blu e nero visibili avvolti attorno al panetto.

---

## 2. 🛡️ Texture degli Oggetti ed Icone Inventario (`textures/item/`)

Tutti gli oggetti utilizzano immagini con **sfondo trasparente** in formato `16x16` pixel.

### 🔷 Icone Equipaggiamento Counter-Terrorist (CT)
1. **`ct_helmet.png` (`16x16`)**:
   - Elmetto tattico militare blu notte / Kevlar. Visiera scura trasparente o occhiali tattici integrati sulla fronte.
2. **`ct_chestplate.png` (`16x16`)**:
   - Gilet tattico modulare (MOLLE) blu scuro/nero con fondina e radio sulla spalla.
3. **`ct_leggings.png` (`16x16`)**:
   - Pantaloni tattici mimetici blu/grigio con ginocchiere rigide nere.
4. **`ct_boots.png` (`16x16`)**:
   - Anfibi militari neri da combattimento con allacciatura rinforzata.

### 🔴 Icone Equipaggiamento Terrorist (T)
1. **`t_helmet.png` (`16x16`)**:
   - Mephisto / Passamontagna nero o bandana rossa/verde attorno alla testa con occhiali da sole scuri.
2. **`t_chestplate.png` (`16x16`)**:
   - Giacca di pelle marrone o gilet mimetico militare deserto (Phoenix Connexion style) su maglietta beige/bianca.
3. **`t_leggings.png` (`16x16`)**:
   - Jeans scuri o pantaloni cargo mimetici verde oliva con tasconi laterali.
4. **`t_boots.png` (`16x16`)**:
   - Scarponi da combattimento marroni/beige da deserto.

### 🛠️ Oggetti Utilità
1. **`defusal_kit.png` (`16x16`)**:
   - Kit di disinnesco CS:GO: Pinze tagliafili rosse/gialle e piccolo multimetro elettronico in una custodia verde militare.
2. **`c4_bomb.png` (`16x16`)**:
   - Icona nell'inventario del C4 con panetto beige e timer digitale.
3. **`bomb_site_wand.png` (`16x16`)**:
   - Bacchetta/Strumento per creatori di mappe: Un bastone di ferro con un'antenna ed un indicatore LED verde in cima.

---

## 3. 👥 Texture delle Entità / Mobs (`textures/entity/`)

### 🔵 `ct.png` (Skin Mob Counter-Terrorist)
- **Dimensione**: `64x64` pixel (formato standard skin Minecraft Steve/Alex).
- **Stile**: SWAT / SAS / SEAL Team 6.
- **Tonalità**: Blu scuro, Nero, Grigio metallo.
- **Dettagli**:
  - **Testa (0,0 - 32,16)**: Elmetto SWAT blu scuro con visiera rinforzata ed un com-link / auricolare.
  - **Busto (16,16 - 40,32)**: Gilet antiproiettile Kevlar con patch "CT" o bandiera sulla spalla.
  - **Braccia & Gambe**: Mimetica militare e guanti di pelle neri.

### 🔴 `t.png` (Skin Mob Terrorist)
- **Dimensione**: `64x64` pixel.
- **Stile**: Phoenix Connexion / Guerilla Warfare (CS:GO).
- **Tonalità**: Beige, Marrone, Verde Oliva, Bandana Rossa/Bianca.
- **Dettagli**:
  - **Testa**: Passamontagna nero/bianco con buchi per gli occhi intensi o calotta deserto.
  - **Busto**: Giacca marrone di pelle su gilet rigido con caricatori AK-47 incrociati sul petto.

---

## 4. 👕 Layer delle Armature sul Giocatore (`textures/models/armor/`)

Queste texture definiscono l'aspetto dell'armatura quando indossata dal giocatore.

### `ct_layer_1.png` (`64x32` o `64x64`)
- Contiene l'aspetto visivo di: **Elmetto**, **Pettorina** e **Stivali** dei CT.
- Colori dominanti: Blu marino `#1a2636`, Nero `#0f0f14`.

### `ct_layer_2.png` (`64x32` o `64x64`)
- Contiene l'aspetto visivo dei: **Pantaloni / Leggings** dei CT.
- Colori dominanti: Grigio scuro con ginocchiere nere.

### `t_layer_1.png` (`64x32` o `64x64`)
- Contiene l'aspetto visivo di: **Elmetto**, **Pettorina** e **Stivali** dei T.
- Colori dominanti: Marrone pelle `#4a2e18`, Verde oliva `#3b4a2e`.

### `t_layer_2.png` (`64x32` o `64x64`)
- Contiene l'aspetto visivo dei: **Pantaloni / Leggings** dei T.
- Colori dominanti: Jeans scuri o cargo deserto.

---

## 🎨 Consigli per la Creazione

1. **Software Consigliati**:
   - **Blockbench**: Eccellente per visualizzare e dipingere texture di blocchi e skin Minecraft in 3D in tempo reale.
   - **GIMP / Photoshop / Aseprite**: Ottimi per disegnare le icone 16x16 ed applicare la trasparenza Alpha.
2. **Formato File**:
   - Salvare sempre in formato **PNG a 32-bit (RGB + canale Alpha per la trasparenza)**.
