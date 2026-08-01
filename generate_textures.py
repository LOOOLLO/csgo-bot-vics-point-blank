import os
from PIL import Image, ImageDraw

BASE_DIR = "/Users/lorenzodard/Library/Application Support/PrismLauncher/instances/mod tests bot/minecraft/mods/csgo-mc-mod/src/main/resources/assets/csgo_mc/textures"

# Ensure output directories exist
os.makedirs(os.path.join(BASE_DIR, "item"), exist_ok=True)
os.makedirs(os.path.join(BASE_DIR, "block"), exist_ok=True)
os.makedirs(os.path.join(BASE_DIR, "entity"), exist_ok=True)
os.makedirs(os.path.join(BASE_DIR, "models/armor"), exist_ok=True)

# Helper function to create RGBA image
def create_img(width, height):
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))

# Colors
C_DARK_NAVY = (24, 34, 50, 255)
C_NAVY = (38, 54, 78, 255)
C_LIGHT_NAVY = (58, 80, 112, 255)
C_BLACK = (20, 20, 24, 255)
C_GREY = (60, 64, 72, 255)
C_LIGHT_GREY = (140, 145, 155, 255)
C_CYAN = (64, 200, 220, 255)
C_RED = (210, 40, 40, 255)
C_DARK_RED = (140, 20, 20, 255)
C_YELLOW = (230, 190, 40, 255)
C_GREEN = (40, 180, 70, 255)
C_BROWN = (110, 70, 40, 255)
C_DARK_BROWN = (70, 42, 22, 255)
C_TAN = (185, 150, 105, 255)
C_OLIVE = (85, 95, 60, 255)
C_SKIN = (220, 170, 130, 255)

# --- 1. ITEM TEXTURES (16x16) ---

# CT Helmet
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
# Dome
draw.rectangle([3, 3, 12, 9], fill=C_NAVY)
draw.rectangle([4, 2, 11, 3], fill=C_LIGHT_NAVY)
# Visor
draw.rectangle([3, 7, 12, 8], fill=C_CYAN)
draw.rectangle([3, 9, 12, 10], fill=C_DARK_NAVY)
# Strap
draw.line([(4, 11), (4, 13)], fill=C_BLACK)
draw.line([(11, 11), (11, 13)], fill=C_BLACK)
draw.line([(5, 13), (10, 13)], fill=C_BLACK)
img.save(os.path.join(BASE_DIR, "item/ct_helmet.png"))

# CT Chestplate
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([3, 3, 12, 13], fill=C_NAVY)
draw.rectangle([5, 4, 10, 10], fill=C_DARK_NAVY) # Plate
draw.rectangle([4, 11, 11, 12], fill=C_BLACK) # Belt
draw.rectangle([7, 11, 8, 12], fill=C_YELLOW) # Buckle
draw.rectangle([4, 6, 5, 8], fill=C_GREY) # Radio
img.save(os.path.join(BASE_DIR, "item/ct_chestplate.png"))

# CT Leggings
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([4, 3, 11, 5], fill=C_DARK_NAVY)
draw.rectangle([4, 6, 7, 13], fill=C_NAVY)
draw.rectangle([8, 6, 11, 13], fill=C_NAVY)
draw.rectangle([4, 9, 6, 11], fill=C_BLACK) # Knee pad L
draw.rectangle([9, 9, 11, 11], fill=C_BLACK) # Knee pad R
img.save(os.path.join(BASE_DIR, "item/ct_leggings.png"))

# CT Boots
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([3, 6, 6, 13], fill=C_BLACK)
draw.rectangle([9, 6, 12, 13], fill=C_BLACK)
draw.rectangle([2, 12, 6, 14], fill=C_GREY)
draw.rectangle([9, 12, 13, 14], fill=C_GREY)
img.save(os.path.join(BASE_DIR, "item/ct_boots.png"))

# T Helmet (Balaclava)
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([3, 2, 12, 12], fill=C_BLACK)
draw.rectangle([5, 5, 10, 7], fill=C_SKIN) # Eye cutout
draw.rectangle([5, 5, 7, 6], fill=C_GREY) # Sunglasses L
draw.rectangle([8, 5, 10, 6], fill=C_GREY) # Sunglasses R
draw.rectangle([3, 2, 12, 3], fill=C_RED) # Red Bandana
img.save(os.path.join(BASE_DIR, "item/t_helmet.png"))

# T Chestplate
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([3, 3, 12, 13], fill=C_BROWN)
draw.rectangle([5, 3, 10, 13], fill=C_TAN)
draw.line([(4, 4), (11, 11)], fill=C_DARK_BROWN) # Strap
draw.rectangle([7, 7, 9, 9], fill=C_OLIVE) # Ammo pouch
img.save(os.path.join(BASE_DIR, "item/t_chestplate.png"))

# T Leggings
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([4, 3, 11, 5], fill=C_DARK_BROWN)
draw.rectangle([4, 6, 7, 13], fill=C_OLIVE)
draw.rectangle([8, 6, 11, 13], fill=C_OLIVE)
draw.rectangle([3, 8, 4, 10], fill=C_TAN) # Cargo pocket L
draw.rectangle([11, 8, 12, 10], fill=C_TAN) # Cargo pocket R
img.save(os.path.join(BASE_DIR, "item/t_leggings.png"))

# T Boots
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([3, 6, 6, 13], fill=C_TAN)
draw.rectangle([9, 6, 12, 13], fill=C_TAN)
draw.rectangle([2, 12, 6, 14], fill=C_DARK_BROWN)
draw.rectangle([9, 12, 13, 14], fill=C_DARK_BROWN)
img.save(os.path.join(BASE_DIR, "item/t_boots.png"))

# Defusal Kit
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([3, 4, 12, 12], fill=C_OLIVE) # Pouch
draw.line([(2, 2), (6, 6)], fill=C_YELLOW) # Wire cutters handle L
draw.line([(7, 2), (3, 6)], fill=C_RED) # Wire cutters handle R
draw.rectangle([8, 6, 11, 10], fill=C_GREY) # Multimeter
draw.point((9, 7), fill=C_GREEN)
img.save(os.path.join(BASE_DIR, "item/defusal_kit.png"))

# C4 Bomb Item
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([2, 4, 13, 11], fill=C_TAN) # C4 brick
draw.rectangle([4, 4, 11, 11], fill=C_TAN)
draw.rectangle([5, 5, 10, 7], fill=C_GREY) # Keypad base
draw.point((6, 6), fill=C_RED) # LED
draw.point((8, 6), fill=C_GREEN)
draw.line([(2, 7), (13, 7)], fill=C_BLACK) # Tape
draw.line([(2, 9), (13, 9)], fill=C_BLACK)
draw.line([(3, 4), (3, 11)], fill=C_RED) # Wire
img.save(os.path.join(BASE_DIR, "item/c4_bomb.png"))

# Bomb Site Wand
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
for i in range(12):
    draw.point((2 + i, 13 - i), fill=C_LIGHT_GREY if i % 2 == 0 else C_GREY)
draw.point((14, 1), fill=C_RED) # Tip LED
draw.point((13, 2), fill=C_GREEN)
img.save(os.path.join(BASE_DIR, "item/bomb_site_wand.png"))

# --- 2. BLOCK TEXTURES (16x16) ---

# Bomb Site Block
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([0, 0, 15, 15], fill=C_GREY) # Concrete background
# Add noise to concrete
for x in range(16):
    for y in range(16):
        if (x + y) % 3 == 0:
            draw.point((x, y), fill=(70, 74, 82, 255))
        elif (x * y) % 5 == 0:
            draw.point((x, y), fill=(50, 54, 60, 255))

# Hazard stripes on borders
for i in range(0, 16, 4):
    draw.polygon([(i, 0), (i+2, 0), (i, 2)], fill=C_YELLOW)
    draw.polygon([(i, 13), (i+2, 15), (i, 15)], fill=C_YELLOW)

# Big red "A" in center
draw.rectangle([5, 4, 10, 5], fill=C_RED)
draw.rectangle([5, 6, 6, 12], fill=C_RED)
draw.rectangle([9, 6, 10, 12], fill=C_RED)
draw.rectangle([6, 8, 9, 9], fill=C_RED)
img.save(os.path.join(BASE_DIR, "block/bomb_site.png"))

# C4 Bomb Block Texture
img = create_img(16, 16)
draw = ImageDraw.Draw(img)
draw.rectangle([0, 0, 15, 15], fill=C_TAN)
for x in range(16):
    for y in range(16):
        if (x + y) % 4 == 0:
            draw.point((x, y), fill=(165, 130, 85, 255))

# Tape
draw.rectangle([0, 4, 15, 6], fill=C_BLACK)
draw.rectangle([0, 10, 15, 12], fill=C_BLACK)

# Display & Wires
draw.rectangle([4, 7, 11, 9], fill=C_GREY)
draw.rectangle([5, 8, 7, 8], fill=C_RED) # Screen
draw.line([(2, 0), (2, 15)], fill=C_RED)
draw.line([(13, 0), (13, 15)], fill=C_CYAN)
img.save(os.path.join(BASE_DIR, "block/c4_bomb.png"))

# --- 3. ARMOR LAYERS (64x32) ---

def create_armor_layer(h_color, c_color, l_color, b_color):
    img = create_img(64, 32)
    draw = ImageDraw.Draw(img)
    # Head (0,0 to 32,16)
    if h_color:
        draw.rectangle([0, 0, 31, 15], fill=h_color)
    # Body (16,16 to 40,32)
    if c_color:
        draw.rectangle([16, 16, 39, 31], fill=c_color)
    # Leggings (0,16 to 16,32 & 40,16 to 64,32)
    if l_color:
        draw.rectangle([0, 16, 15, 31], fill=l_color)
        draw.rectangle([40, 16, 63, 31], fill=l_color)
    # Boots
    if b_color:
        draw.rectangle([0, 24, 15, 31], fill=b_color)
    return img

ct_l1 = create_armor_layer(C_NAVY, C_NAVY, None, C_BLACK)
ct_l1.save(os.path.join(BASE_DIR, "models/armor/ct_layer_1.png"))

ct_l2 = create_armor_layer(None, None, C_DARK_NAVY, None)
ct_l2.save(os.path.join(BASE_DIR, "models/armor/ct_layer_2.png"))

t_l1 = create_armor_layer(C_BLACK, C_BROWN, None, C_TAN)
t_l1.save(os.path.join(BASE_DIR, "models/armor/t_layer_1.png"))

t_l2 = create_armor_layer(None, None, C_OLIVE, None)
t_l2.save(os.path.join(BASE_DIR, "models/armor/t_layer_2.png"))

# --- 4. MOB SKINS (64x64) ---

def create_mob_skin(is_ct):
    img = create_img(64, 64)
    draw = ImageDraw.Draw(img)
    
    main_color = C_NAVY if is_ct else C_OLIVE
    sec_color = C_DARK_NAVY if is_ct else C_BROWN
    accent = C_CYAN if is_ct else C_RED
    
    # Head
    draw.rectangle([0, 0, 31, 15], fill=sec_color)
    draw.rectangle([8, 8, 23, 15], fill=C_SKIN) # Face
    if is_ct:
        draw.rectangle([8, 10, 23, 12], fill=C_BLACK) # Visor
    else:
        draw.rectangle([8, 8, 23, 15], fill=C_BLACK) # Balaclava
        draw.rectangle([10, 10, 14, 12], fill=C_SKIN)
        draw.rectangle([17, 10, 21, 12], fill=C_SKIN)

    # Body
    draw.rectangle([16, 16, 39, 31], fill=main_color)
    draw.rectangle([20, 20, 35, 28], fill=sec_color) # Vest
    
    # Arms
    draw.rectangle([40, 16, 55, 31], fill=main_color)
    draw.rectangle([32, 48, 47, 63], fill=main_color)
    
    # Legs
    draw.rectangle([0, 16, 15, 31], fill=sec_color)
    draw.rectangle([0, 48, 15, 63], fill=sec_color)
    
    return img

ct_skin = create_mob_skin(True)
ct_skin.save(os.path.join(BASE_DIR, "entity/ct.png"))

t_skin = create_mob_skin(False)
t_skin.save(os.path.join(BASE_DIR, "entity/t.png"))

print("All CS:GO mod textures generated successfully!")
