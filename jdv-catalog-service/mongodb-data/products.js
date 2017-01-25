db = db.getSiblingDB('catalogdb');
db.product.insertMany( [
    { 'itemId' : '329299', 'name' : 'Red Fedora', 'description' : 'Official Red Hat Fedora', 'price' : 34.99 },
    { 'itemId' : '329199', 'name' : 'Forge Laptop Sticker', 'description' : 'JBoss Community Forge Project Sticker', 'price' : 8.50},
    { 'itemId' : '165613', 'name' : 'Solid Performance Polo', 'description' : 'Moisture-wicking, antimicrobial 100% polyester design wicks for life of garment. No-curl, rib-knit collar; special collar band maintains crisp fold; three-button placket with dyed-to-match buttons; hemmed sleeves; even bottom with side vents; Import. Embroidery. Red Pepper.', 'price' : 17.80},
    { 'itemId' : '165614', 'name' : 'Ogio Caliber Polo', 'description' : 'Moisture-wicking 100% polyester. Rib-knit collar and cuffs; Ogio jacquard tape inside neck; bar-tacked three-button placket with Ogio dyed-to-match buttons; side vents; tagless; Ogio badge on left sleeve. Import. Embroidery. Black.', 'price' : 28.75},
    { 'itemId' : '165954', 'name' : '16 oz. Vortex Tumbler', 'description' : 'Double-wall insulated, BPA-free, acrylic cup. Push-on lid with thumb-slide closure; for hot and cold beverages. Holds 16 oz. Hand wash only. Imprint. Clear.', 'price' : 6.00},
    { 'itemId' : '444434', 'name' : 'Pebble Smart Watch', 'description' : 'Smart glasses and smart watches are perhaps two of the most exciting developments in recent years. ', 'price' : 24.00},
    { 'itemId' : '444435', 'name' : 'Oculus Rift', 'description' : 'The world of gaming has also undergone some very unique and compelling tech advances in recent years. Virtual reality, the concept of complete immersion into a digital universe through a special headset, has been the white whale of gaming and digital technology ever since Geekstakes Oculus Rift Giveaway Nintendo marketed its Virtual Boy gaming system in 1995.Lytro', 'price' : 106.00},
    { 'itemId' : '444436', 'name' : 'Lytro Camera', 'description' : 'Consumers who want to up their photography game are looking at newfangled cameras like the Lytro Field camera, designed to take photos with infinite focus, so you can decide later exactly where you want the focus of each image to be. ', 'price' : 44.30}
]);

