package com.pfe.marketplace.demo;

import java.util.List;

/** Static reference data for the demo seed: categories, shops and a realistic product catalog. */
final class DemoCatalog {

    private DemoCatalog() {
    }

    record CategoryDef(String key, String name, String parentKey, String color) {
    }

    record ShopDef(String name, String description, List<String> categoryKeys, String commissionRate) {
    }

    record ProductDef(String categoryKey, String name, double price, String blurb) {
    }

    static final List<CategoryDef> CATEGORIES = List.of(
            new CategoryDef("electronics", "Electronics", null, "#3b82f6"),
            new CategoryDef("phones", "Smartphones & Tablets", "electronics", "#2563eb"),
            new CategoryDef("audio", "Audio", "electronics", "#7c3aed"),
            new CategoryDef("computers", "Computers & Accessories", "electronics", "#0ea5e9"),
            new CategoryDef("home", "Home & Kitchen", null, "#f59e0b"),
            new CategoryDef("kitchen", "Kitchen", "home", "#d97706"),
            new CategoryDef("decor", "Decor & Lighting", "home", "#ea580c"),
            new CategoryDef("fashion", "Fashion", null, "#ec4899"),
            new CategoryDef("clothing", "Clothing", "fashion", "#db2777"),
            new CategoryDef("shoes", "Shoes & Bags", "fashion", "#be185d"),
            new CategoryDef("beauty", "Beauty & Health", null, "#14b8a6"),
            new CategoryDef("sports", "Sports & Outdoors", null, "#22c55e"),
            new CategoryDef("books", "Books & Stationery", null, "#a16207"),
            new CategoryDef("toys", "Toys & Games", null, "#ef4444"));

    /** The last shop stays PENDING so the admin can demo seller validation. */
    static final List<ShopDef> SHOPS = List.of(
            new ShopDef("TechNova", "Smartphones, laptops and smart gadgets at fair prices.",
                    List.of("phones", "computers"), null),
            new ShopDef("SoundWave Audio", "Headphones, speakers and everything that sounds good.",
                    List.of("audio"), null),
            new ShopDef("Maison Belle", "Kitchenware and decoration for a beautiful home.",
                    List.of("kitchen", "decor"), null),
            new ShopDef("Urban Threads", "Streetwear, basics and accessories.",
                    List.of("clothing", "shoes"), "12.00"),
            new ShopDef("Glow Beauty", "Clean skincare and wellness essentials.",
                    List.of("beauty"), null),
            new ShopDef("PeakSport", "Gear for running, fitness and the outdoors.",
                    List.of("sports"), null),
            new ShopDef("PageTurner", "Books, notebooks, board games and toys.",
                    List.of("books", "toys"), "8.00"),
            new ShopDef("Green Corner", "Plants and eco-friendly home products (awaiting validation).",
                    List.of("decor"), null));

    static final List<ProductDef> PRODUCTS = List.of(
            // Smartphones & Tablets
            p("phones", "Nova X5 Smartphone 128 GB", 349, "6.5\" AMOLED display, 48 MP camera and 2-day battery."),
            p("phones", "Nova X5 Pro Smartphone 256 GB", 529, "Flagship performance with a 120 Hz screen and 4K video."),
            p("phones", "Lumi Tab 10 Tablet", 229, "10-inch tablet for streaming, reading and video calls."),
            p("phones", "Lumi Tab Mini 8", 159, "Compact tablet that fits in a jacket pocket."),
            p("phones", "Fast Charger 65 W USB-C", 29.9, "Charges a phone to 50 % in 20 minutes."),
            p("phones", "MagSafe-style Wireless Charger", 24.9, "15 W magnetic wireless charging pad."),
            p("phones", "Tempered Glass Screen Protector (2-pack)", 9.9, "9H hardness, bubble-free install kit."),
            p("phones", "Shockproof Phone Case", 14.9, "Military-grade drop protection, slim profile."),
            p("phones", "Power Bank 20 000 mAh", 39.9, "Charges two devices at once, LED indicator."),
            p("phones", "Smartwatch Fit 3", 89, "Heart rate, sleep tracking and 14-day battery."),
            // Audio
            p("audio", "Wireless Noise-Cancelling Headphones", 129, "Active noise cancelling and 30 h playtime."),
            p("audio", "True Wireless Earbuds Air", 59.9, "Touch controls, IPX5, charging case included."),
            p("audio", "True Wireless Earbuds Pro ANC", 99, "Hybrid ANC with transparency mode."),
            p("audio", "Portable Bluetooth Speaker Mini", 34.9, "Waterproof speaker with punchy bass."),
            p("audio", "Bluetooth Party Speaker 60 W", 149, "RGB lights, karaoke input, 12 h battery."),
            p("audio", "Studio Monitor Headphones", 79, "Flat response for mixing and podcasting."),
            p("audio", "USB Podcast Microphone", 69, "Cardioid condenser mic with mute button."),
            p("audio", "Kids Volume-Limited Headphones", 24.9, "85 dB safe listening, foldable."),
            p("audio", "Soundbar 2.1 with Subwoofer", 189, "Cinema sound for your TV, HDMI ARC."),
            p("audio", "Vinyl Record Player", 119, "Belt-drive turntable with built-in speakers."),
            // Computers
            p("computers", "UltraBook 14 Laptop i5 16 GB", 799, "Aluminium chassis, 512 GB SSD, 1.3 kg."),
            p("computers", "Gaming Laptop 15 RTX", 1199, "144 Hz display and dedicated graphics."),
            p("computers", "Wireless Ergonomic Mouse", 29.9, "Silent clicks, 18-month battery."),
            p("computers", "Mechanical Keyboard TKL", 69, "Hot-swappable switches, RGB backlight."),
            p("computers", "27\" 4K Monitor", 299, "IPS panel, USB-C 65 W power delivery."),
            p("computers", "USB-C Hub 7-in-1", 34.9, "HDMI 4K, SD card, 3x USB, PD charging."),
            p("computers", "1080p Webcam with Microphone", 44.9, "Auto light correction, privacy shutter."),
            p("computers", "Portable SSD 1 TB", 89, "Up to 1050 MB/s, shock resistant."),
            p("computers", "Laptop Stand Aluminium", 27.9, "Adjustable height, foldable."),
            p("computers", "Wi-Fi 6 Mesh Router (2-pack)", 159, "Whole-home coverage up to 400 m²."),
            // Kitchen
            p("kitchen", "Non-stick Frying Pan 28 cm", 32.9, "PFOA-free coating, induction compatible."),
            p("kitchen", "Stainless Steel Cookware Set (6 pcs)", 119, "Tri-ply base, oven safe to 260 °C."),
            p("kitchen", "Electric Kettle 1.7 L", 34.9, "Rapid boil, auto shut-off, glass body."),
            p("kitchen", "Espresso Machine 15 bar", 179, "Steam wand for cappuccinos at home."),
            p("kitchen", "Air Fryer 5.5 L", 89, "Crispy results with 80 % less oil."),
            p("kitchen", "Chef's Knife 20 cm", 49, "German steel, full tang, ergonomic handle."),
            p("kitchen", "Bamboo Cutting Board Set", 24.9, "Three sizes with juice grooves."),
            p("kitchen", "Glass Food Containers (10 pcs)", 29.9, "Leak-proof lids, microwave safe."),
            p("kitchen", "Hand Blender 800 W", 39.9, "Whisk and chopper attachments included."),
            p("kitchen", "Cast Iron Dutch Oven 5 L", 69, "Enamelled, perfect for slow cooking."),
            // Decor
            p("decor", "Scandinavian Table Lamp", 45, "Oak base and linen shade, warm light."),
            p("decor", "LED Strip Lights 10 m", 22.9, "App-controlled colours and music sync."),
            p("decor", "Velvet Cushion Covers (set of 2)", 19.9, "45 x 45 cm, hidden zipper."),
            p("decor", "Ceramic Vase Set", 34.9, "Three matte vases in earthy tones."),
            p("decor", "Wall Mirror Round 60 cm", 59, "Thin metal frame, ready to hang."),
            p("decor", "Scented Soy Candle Trio", 27.5, "Fig, cedar and vanilla, 40 h burn each."),
            p("decor", "Cotton Throw Blanket", 39, "Soft knit, 130 x 170 cm."),
            p("decor", "Framed Botanical Prints (3 pcs)", 44.9, "A4 prints in oak frames."),
            p("decor", "Floor Lamp Arc", 89, "Adjustable arc lamp with marble base."),
            p("decor", "Macramé Plant Hanger", 14.9, "Handmade, fits pots up to 20 cm."),
            // Clothing
            p("clothing", "Organic Cotton T-Shirt", 19.9, "Regular fit, 180 g/m² heavyweight cotton."),
            p("clothing", "Oversized Hoodie", 44.9, "Brushed fleece interior, kangaroo pocket."),
            p("clothing", "Slim Fit Jeans", 49.9, "Stretch denim with a vintage wash."),
            p("clothing", "Puffer Jacket", 89, "Water-repellent, recycled insulation."),
            p("clothing", "Linen Shirt", 39.9, "Breathable linen for summer days."),
            p("clothing", "Merino Wool Sweater", 69, "Fine knit, naturally temperature regulating."),
            p("clothing", "Cargo Trousers", 54.9, "Relaxed fit with six pockets."),
            p("clothing", "Sports Leggings", 29.9, "High waist, squat-proof fabric."),
            p("clothing", "Rain Jacket", 59.9, "Packable, taped seams, adjustable hood."),
            p("clothing", "Pack of 5 Cotton Socks", 12.9, "Reinforced heel and toe."),
            // Shoes & Bags
            p("shoes", "Classic White Sneakers", 69, "Leather upper, cushioned insole."),
            p("shoes", "Running Shoes Glide", 89, "Lightweight foam and breathable mesh."),
            p("shoes", "Chelsea Boots", 99, "Suede with elastic side panels."),
            p("shoes", "Canvas Backpack 20 L", 39.9, "Padded laptop sleeve up to 15\"."),
            p("shoes", "Leather Crossbody Bag", 59, "Adjustable strap, three compartments."),
            p("shoes", "Weekend Duffel Bag", 64.9, "Water-resistant, shoe compartment."),
            p("shoes", "Slide Sandals", 24.9, "Contoured footbed, quick-dry."),
            p("shoes", "Minimalist Leather Wallet", 29.9, "RFID blocking, holds 8 cards."),
            p("shoes", "Hiking Boots Waterproof", 119, "Vibram-style outsole, ankle support."),
            p("shoes", "Tote Bag Canvas", 17.9, "Heavy canvas with inner pocket."),
            // Beauty
            p("beauty", "Hyaluronic Acid Serum 30 ml", 18.9, "Deep hydration for all skin types."),
            p("beauty", "Vitamin C Brightening Serum", 22.9, "15 % vitamin C with ferulic acid."),
            p("beauty", "Daily Moisturiser SPF 30", 19.9, "Lightweight, non-greasy finish."),
            p("beauty", "Gentle Foaming Cleanser", 12.9, "Soap-free, pH balanced."),
            p("beauty", "Argan Hair Oil", 16.9, "Cold-pressed, tames frizz."),
            p("beauty", "Electric Toothbrush Sonic", 49, "Five modes, two-minute timer."),
            p("beauty", "Ionic Hair Dryer 2000 W", 54.9, "Fast drying with less frizz."),
            p("beauty", "Natural Deodorant", 9.9, "Aluminium-free, 48 h freshness."),
            p("beauty", "Bamboo Makeup Brush Set", 24.9, "Ten vegan brushes with pouch."),
            p("beauty", "Aromatherapy Diffuser", 34.9, "Ultrasonic, seven LED colours."),
            // Sports
            p("sports", "Yoga Mat 6 mm", 29.9, "Non-slip, eco TPE with carry strap."),
            p("sports", "Adjustable Dumbbells 20 kg", 129, "Replace 15 sets of weights."),
            p("sports", "Resistance Bands Set", 19.9, "Five levels with handles and door anchor."),
            p("sports", "Insulated Water Bottle 750 ml", 22.9, "Keeps drinks cold for 24 h."),
            p("sports", "Foam Roller", 24.9, "High-density for muscle recovery."),
            p("sports", "Camping Tent 2 Persons", 99, "Pops up in 2 minutes, waterproof."),
            p("sports", "Trekking Poles (pair)", 39.9, "Aluminium, anti-shock, cork grips."),
            p("sports", "Jump Rope Speed", 12.9, "Ball bearings, adjustable cable."),
            p("sports", "Cycling Helmet", 49.9, "In-mould shell with rear LED."),
            p("sports", "Fitness Tracker Band", 39.9, "Steps, heart rate and sleep."),
            // Books & Stationery
            p("books", "The Pragmatic Developer (paperback)", 34.9, "Timeless advice for software craftspeople."),
            p("books", "Data Science from Scratch", 39.9, "First principles with Python."),
            p("books", "World Cuisine Cookbook", 29.9, "200 recipes from 40 countries."),
            p("books", "Mindful Mornings Journal", 16.9, "Guided prompts for 90 days."),
            p("books", "Dotted Notebook A5", 12.9, "160 pages, 120 g/m² paper."),
            p("books", "Fountain Pen Starter Kit", 24.9, "Steel nib with 6 ink cartridges."),
            p("books", "Watercolour Paint Set 36", 27.9, "Artist-grade pigments, travel tin."),
            p("books", "Graphic Novel Box Set", 59, "Complete trilogy, hardcover."),
            p("books", "Desk Organiser Bamboo", 21.9, "Five compartments and phone slot."),
            p("books", "Sci-Fi Classics Collection", 44.9, "Five novels that shaped the genre."),
            // Toys & Games
            p("toys", "Strategy Board Game Settlers", 39.9, "Trade, build and settle; 3-4 players."),
            p("toys", "Cooperative Puzzle Game", 24.9, "Work together to escape the island."),
            p("toys", "1000-Piece Jigsaw Puzzle", 17.9, "Panoramic city skyline."),
            p("toys", "Building Bricks Set 500 pcs", 34.9, "Compatible with major brands."),
            p("toys", "Remote Control Car Off-Road", 49.9, "30 km/h with rechargeable battery."),
            p("toys", "Wooden Train Set", 44.9, "50 pieces, figure-eight track."),
            p("toys", "Plush Bear 40 cm", 19.9, "Ultra-soft, machine washable."),
            p("toys", "Kids Science Experiment Kit", 29.9, "30 experiments with guide book."),
            p("toys", "Card Game Party Pack", 14.9, "Fast and funny, 2-8 players."),
            p("toys", "Drone Mini with Camera", 69, "Altitude hold and one-key return."),
            // Extra best-sellers
            p("audio", "Over-Ear Headphones Retro", 49.9, "Foldable design with detachable cable."),
            p("audio", "Smart Speaker with Voice Assistant", 79, "Room-filling sound and smart-home control."),
            p("kitchen", "Digital Kitchen Scale", 15.9, "Precise to 1 g, tare function."),
            p("kitchen", "French Press Coffee Maker 1 L", 26.9, "Double-wall stainless steel."),
            p("clothing", "Classic Denim Jacket", 64.9, "Timeless cut with button-flap pockets."),
            p("clothing", "Fleece Zip Jacket", 39.9, "Warm recycled fleece for cool evenings."),
            p("sports", "Pull-Up Bar Doorway", 29.9, "No screws needed, holds 120 kg."),
            p("sports", "Sports Duffel Bag 40 L", 34.9, "Separate wet compartment."),
            p("toys", "Magnetic Tiles 60 pcs", 44.9, "Creative 3D building for ages 3+."),
            p("books", "Productivity Planner 2027", 19.9, "Weekly layout with goal tracking."));

    private static ProductDef p(String category, String name, double price, String blurb) {
        return new ProductDef(category, name, price, blurb);
    }
}
